package com.moviebox.app.security

import android.content.Context
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.gson.JsonParser
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class NativeDeveloperInfo(
    val developerName: String,
    val telegramUrl: String,
    val telegramHandle: String,
    val securityBadge: String,
    val soLibraryName: String,
    val vaultSha256Short: String,
    val firebaseAppId: String = "",
    val firebaseApiKey: String = "",
    val firebaseDatabaseUrl: String = "",
    val firebaseSenderId: String = "",
    val firebaseProjectId: String = "",
    val firebaseStorageBucket: String = "",
    val tmdbApiKey: String = "",
    val isTampered: Boolean,
    val tamperReason: String? = null
)

object NhNativeSecurityVault {

    private const val LIB_FILE_NAME = "libnh_security.so"
    private const val EXPECTED_VAULT_SHA256 = "45a0349c630e3512ca37a5a55da519fd30ecc5706cd342ab1f6d25782371ed0e"
    private const val PBKDF2_ROUNDS = 12000
    private const val OBF_KEY_MASK = 167

    private val OBFUSCATED_PASSPHRASE = intArrayOf(
        0xE9, 0xEF, 0xF8, 0xEA, 0xC8, 0xD1, 0xCE, 0xC2, 0xE5, 0xC8, 0xDF, 0xF8, 0xE9, 0xC6, 0xD3, 0xCE, 0xD1, 0xC2, 0xF8, 0xF4, 0xC8, 0xF8, 0xEC, 0xC2, 0xDE, 0xF8, 0x95, 0x97, 0x95, 0x91, 0x86, 0xE7
    )

    private val MAGIC_START = byteArrayOf(
        0x89.toByte(), 0x4E, 0x48, 0x56, 0x4C, 0x54, 0x31, 0x32
    )
    private val MAGIC_END = byteArrayOf(
        0x45, 0x4E, 0x44, 0x4E, 0x48, 0x56, 0x31, 0x32
    )

    @Volatile
    private var cachedInfo: NativeDeveloperInfo? = null

    fun getDeveloperInfo(context: Context): NativeDeveloperInfo {
        cachedInfo?.let { return it }
        synchronized(this) {
            cachedInfo?.let { return it }
            val loaded = verifyAndDecryptFromNativeSo(context.applicationContext)
            cachedInfo = loaded
            return loaded
        }
    }

    fun getCachedTmdbApiKey(): String = cachedInfo?.tmdbApiKey.orEmpty()

    /**
     * Initializes FirebaseApp programmatically from the encrypted native `.so` vault
     * so no plaintext Google API key or Firebase RTDB URL is needed in resources.arsc.
     */
    fun ensureFirebaseInitialized(context: Context) {
        val appContext = context.applicationContext
        val info = getDeveloperInfo(appContext)
        if (info.isTampered || info.firebaseAppId.isBlank() || info.firebaseApiKey.isBlank()) return
        runCatching {
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(info.firebaseAppId)
                    .setApiKey(info.firebaseApiKey)
                    .setDatabaseUrl(info.firebaseDatabaseUrl)
                    .setGcmSenderId(info.firebaseSenderId)
                    .setProjectId(info.firebaseProjectId)
                    .setStorageBucket(info.firebaseStorageBucket)
                    .build()
                FirebaseApp.initializeApp(appContext, options)
            }
        }
    }

    private fun verifyAndDecryptFromNativeSo(context: Context): NativeDeveloperInfo {
        runCatching { System.loadLibrary("nh_security") }

        val soBytes = readNativeSoBytes(context)
            ?: return tamperFailure("Native security library ($LIB_FILE_NAME) is missing from APK.")

        val startIdx = indexOfSubArray(soBytes, MAGIC_START)
        if (startIdx < 0) {
            return tamperFailure("Encrypted vault header not found in $LIB_FILE_NAME.")
        }
        val endIdx = indexOfSubArray(soBytes, MAGIC_END, startIdx)
        if (endIdx < 0 || endIdx <= startIdx) {
            return tamperFailure("Encrypted vault trailer corrupted in $LIB_FILE_NAME.")
        }

        val vaultBlob = soBytes.copyOfRange(startIdx, endIdx + MAGIC_END.size)

        val actualSha256 = sha256Hex(vaultBlob)
        if (!actualSha256.equals(EXPECTED_VAULT_SHA256, ignoreCase = true)) {
            return tamperFailure("SHA-256 integrity mismatch in $LIB_FILE_NAME (file was modified).")
        }

        if (vaultBlob.size < 8 + 4 + 4 + 16 + 16 + 32 + 8) {
            return tamperFailure("Truncated vault structure in $LIB_FILE_NAME.")
        }

        val bb = ByteBuffer.wrap(vaultBlob, 8, 8).order(ByteOrder.BIG_ENDIAN)
        val version = bb.int
        val cipherLen = bb.int
        if (version != 12 || cipherLen <= 0 || 48 + cipherLen + 40 != vaultBlob.size) {
            return tamperFailure("Invalid vault version or length in $LIB_FILE_NAME.")
        }

        val salt = vaultBlob.copyOfRange(16, 32)
        val iv = vaultBlob.copyOfRange(32, 48)
        val ciphertext = vaultBlob.copyOfRange(48, 48 + cipherLen)
        val expectedHmac = vaultBlob.copyOfRange(48 + cipherLen, 48 + cipherLen + 32)
        val signedRegion = vaultBlob.copyOfRange(0, 48 + cipherLen)

        val passChars = CharArray(OBFUSCATED_PASSPHRASE.size) { idx ->
            (OBFUSCATED_PASSPHRASE[idx] xor OBF_KEY_MASK).toChar()
        }
        val spec = PBEKeySpec(passChars, salt, PBKDF2_ROUNDS, 256)
        val masterKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
        passChars.fill('\u0000')

        val kEnc = hmacSha256(masterKey, "NH_ENC_V12".toByteArray(Charsets.UTF_8))
        val kMac = hmacSha256(masterKey, "NH_MAC_V12".toByteArray(Charsets.UTF_8))

        val computedHmac = hmacSha256(kMac, signedRegion)
        if (!MessageDigest.isEqual(computedHmac, expectedHmac)) {
            return tamperFailure("HMAC-SHA256 authentication failed for $LIB_FILE_NAME.")
        }

        val keystream = generateKeystream(kEnc, iv, cipherLen)
        val plainBytes = ByteArray(cipherLen)
        for (i in 0 until cipherLen) {
            val rot = (ciphertext[i].toInt() xor keystream[i].toInt()) and 0xFF
            val unrot = ((rot ushr 3) or (rot shl 5)) and 0xFF
            val orig = unrot xor ((i * 37 + 0x5D) and 0xFF)
            plainBytes[i] = orig.toByte()
        }

        return runCatching {
            val jsonStr = String(plainBytes, Charsets.UTF_8)
            val obj = JsonParser.parseString(jsonStr).asJsonObject
            val devName = obj.get("dev")?.asString.orEmpty()
            val tgUrl = obj.get("tg_url")?.asString.orEmpty()
            val tgHandle = obj.get("tg_handle")?.asString.orEmpty()
            val badge = obj.get("badge")?.asString.orEmpty()
            val sig = obj.get("sig")?.asString.orEmpty()

            if (devName.isBlank() || tgUrl.isBlank() || sig != "NH-MOVIEBOX-V15-OFFICIAL-LOCK") {
                return tamperFailure("Decrypted developer signature invalid.")
            }

            NativeDeveloperInfo(
                developerName = devName,
                telegramUrl = tgUrl,
                telegramHandle = tgHandle,
                securityBadge = badge,
                soLibraryName = LIB_FILE_NAME,
                vaultSha256Short = actualSha256.take(16).uppercase(Locale.US),
                firebaseAppId = obj.get("fb_app_id")?.asString.orEmpty(),
                firebaseApiKey = obj.get("fb_api_key")?.asString.orEmpty(),
                firebaseDatabaseUrl = obj.get("fb_db_url")?.asString.orEmpty(),
                firebaseSenderId = obj.get("fb_sender_id")?.asString.orEmpty(),
                firebaseProjectId = obj.get("fb_project_id")?.asString.orEmpty(),
                firebaseStorageBucket = obj.get("fb_bucket")?.asString.orEmpty(),
                tmdbApiKey = obj.get("tmdb_key")?.asString.orEmpty(),
                isTampered = false,
                tamperReason = null
            )
        }.getOrElse {
            tamperFailure("Failed to parse native developer vault.")
        }
    }

    private fun readNativeSoBytes(context: Context): ByteArray? {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        if (!nativeDir.isNullOrBlank()) {
            val file = File(nativeDir, LIB_FILE_NAME)
            if (file.exists() && file.length() > 64) {
                runCatching { return file.readBytes() }
            }
        }

        val apkPaths = buildList {
            add(context.applicationInfo.sourceDir)
            context.applicationInfo.splitSourceDirs?.let { addAll(it) }
        }.filter { !it.isNullOrBlank() }

        val supportedAbis = Build.SUPPORTED_ABIS.toList() + listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        for (apkPath in apkPaths) {
            runCatching {
                ZipFile(apkPath).use { zf ->
                    for (abi in supportedAbis.distinct()) {
                        val entry = zf.getEntry("lib/$abi/$LIB_FILE_NAME")
                        if (entry != null) {
                            return zf.getInputStream(entry).use { it.readBytes() }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun generateKeystream(key: ByteArray, iv: ByteArray, length: Int): ByteArray {
        val out = ByteArray(length)
        var offset = 0
        var blockIdx = 0
        while (offset < length) {
            val counter = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(blockIdx).array()
            val input = ByteArray(iv.size + 4)
            System.arraycopy(iv, 0, input, 0, iv.size)
            System.arraycopy(counter, 0, input, iv.size, 4)
            val block = hmacSha256(key, input)
            val copyLen = minOf(block.size, length - offset)
            System.arraycopy(block, 0, out, offset, copyLen)
            offset += copyLen
            blockIdx++
        }
        return out
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    private fun indexOfSubArray(haystack: ByteArray, needle: ByteArray, startFrom: Int = 0): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        val maxIdx = haystack.size - needle.size
        outer@ for (i in startFrom.coerceAtLeast(0)..maxIdx) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun tamperFailure(reason: String): NativeDeveloperInfo = NativeDeveloperInfo(
        developerName = "TAMPERED",
        telegramUrl = "",
        telegramHandle = "",
        securityBadge = "TAMPER DETECTED",
        soLibraryName = LIB_FILE_NAME,
        vaultSha256Short = "INVALID",
        isTampered = true,
        tamperReason = reason
    )
}
