package com.moviebox.app.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import com.moviebox.app.BuildConfig
import com.moviebox.app.data.model.UserPresenceRecord
import com.moviebox.app.security.NhNativeSecurityVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale

/**
 * Fetches the real Android hardware/system Device ID (`Settings.Secure.ANDROID_ID`),
 * generates a permanent 5-digit Share Code (`10000`–`99999`), registers the device in
 * Firebase Realtime Database (`/users/{uid}` and `/shareCodes/{shareCode}`), and manages
 * the Points & 5-Digit Share Code Redemption system.
 *
 * STRICT FIREBASE VALIDATION:
 * A 5-digit Share Code can ONLY be redeemed if it is actually created/registered in Firebase
 * by another device (User A can redeem User B's Firebase code, or User B can redeem User A's
 * Firebase code). Random 5-digit numbers not present in Firebase are strictly rejected.
 */
object UserPresenceManager {

    const val OWNER_ADMIN_DEVICE_ID = "e029f5624b252c8d"

    fun isOwnerAdminDevice(context: Context? = appContext): Boolean {
        val currentId = getRealDeviceId(context).trim()
        return currentId.equals(OWNER_ADMIN_DEVICE_ID, ignoreCase = true)
    }

    private const val PREFS = "nh_user_presence"
    private const val KEY_REAL_DEVICE_ID = "real_device_id"
    private const val KEY_SHARE_CODE = "share_code_5digit"
    private const val KEY_POINTS = "user_points"
    private const val KEY_SHARE_COUNT = "user_share_count"
    private const val KEY_REDEEMED_CODE = "user_redeemed_code"
    private const val KEY_REDEEMED_CODES_SET = "user_redeemed_codes_set"
    private const val KEY_IS_VIP = "user_is_vip"
    private const val KEY_UNLOCKED_ITEMS = "user_unlocked_items"
    private const val KEY_LAST_SYNCED_OVERRIDE_TS = "last_synced_override_ts"
    private const val KEY_LAST_SYNCED_REFERRAL_COUNT = "last_synced_referral_count"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var appContext: Context? = null
    private var auth: FirebaseAuth? = null
    private var db: FirebaseDatabase? = null
    private var started = false
    private var foreground = true
    private var anonymousAttempted = false
    private var isRedeemInProgress = false

    private var currentUid: String? = null
    private var currentRef: DatabaseReference? = null
    private var userSelfListener: ValueEventListener? = null
    private var connectedRef: DatabaseReference? = null
    private var connectedListener: ValueEventListener? = null
    private var overrideRef: DatabaseReference? = null
    private var overrideListener: ValueEventListener? = null
    private var shareCodeRef: DatabaseReference? = null
    private var shareCodeListener: ValueEventListener? = null

    private val _currentUserRecord = MutableStateFlow(UserPresenceRecord())
    val currentUserRecord: StateFlow<UserPresenceRecord> = _currentUserRecord.asStateFlow()

    fun start(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        loadLocalStateIntoFlow(ctx)

        if (started) return
        started = true
        runCatching {
            NhNativeSecurityVault.ensureFirebaseInitialized(ctx)
            auth = FirebaseAuth.getInstance()
            val dbUrl = NhNativeSecurityVault.getDeveloperInfo(ctx).firebaseDatabaseUrl
            db = if (dbUrl.isNotBlank()) FirebaseDatabase.getInstance(dbUrl) else FirebaseDatabase.getInstance()
        }

        attachDeviceOverrideListener()
        attachShareCodeReferralListener()

        val firebaseAuth = auth ?: return
        firebaseAuth.addAuthStateListener { fa ->
            val user = fa.currentUser
            if (user == null) {
                detachPresence()
                signInAnonymouslyOnce(fa)
            } else {
                attachPresence(user)
            }
        }
    }

    /**
     * Fetches the real hardware/system Device ID using `Settings.Secure.ANDROID_ID`
     * with a deterministic hardware fingerprint fallback so the same device always
     * retains the exact same ID and 5-digit Share Code.
     */
    @SuppressLint("HardwareIds")
    fun getRealDeviceId(context: Context? = appContext): String {
        val ctx = context?.applicationContext ?: appContext
        val prefs = ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = prefs?.getString(KEY_REAL_DEVICE_ID, null)
        if (!cached.isNullOrBlank() && !cached.contains("-")) {
            return cached
        }

        val androidId = runCatching {
            if (ctx != null) {
                Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
            } else null
        }.getOrNull()?.trim()?.lowercase(Locale.US).orEmpty()

        val validAndroidId = androidId.takeIf {
            it.length >= 8 && it != "9774d56d682e549c" && it != "unknown"
        }

        val resolvedId = validAndroidId ?: run {
            val hwFingerprint = listOf(
                Build.BOARD,
                Build.BRAND,
                Build.DEVICE,
                Build.HARDWARE,
                Build.MANUFACTURER,
                Build.MODEL,
                Build.PRODUCT
            ).joinToString("_")
            val digest = MessageDigest.getInstance("SHA-256").digest(hwFingerprint.toByteArray(Charsets.UTF_8))
            digest.take(8).joinToString("") { String.format(Locale.US, "%02x", it) }
        }

        prefs?.edit()?.putString(KEY_REAL_DEVICE_ID, resolvedId)?.apply()
        return resolvedId
    }

    /**
     * Returns the permanent 5-digit Share Code (`10000`–`99999`) for this device.
     */
    fun getFiveDigitShareCode(context: Context? = appContext): String {
        val ctx = context?.applicationContext ?: appContext
        val prefs = ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs?.getString(KEY_SHARE_CODE, null)
        if (!existing.isNullOrBlank() && existing.length == 5 && existing.all { it.isDigit() }) {
            return existing
        }
        val devId = getRealDeviceId(ctx)
        val generated = UserPresenceRecord.generateFiveDigitShareCode(devId)
        prefs?.edit()?.putString(KEY_SHARE_CODE, generated)?.apply()
        return generated
    }

    private fun loadLocalStateIntoFlow(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val devId = getRealDeviceId(ctx)
        val code = getFiveDigitShareCode(ctx)
        val pts = prefs.getInt(KEY_POINTS, 0).coerceAtLeast(0)
        val shares = prefs.getInt(KEY_SHARE_COUNT, 0).coerceAtLeast(0)
        val redeemedSet = prefs.getStringSet(KEY_REDEEMED_CODES_SET, emptySet()).orEmpty().toList()
        val redeemedSingle = prefs.getString(KEY_REDEEMED_CODE, null) ?: redeemedSet.firstOrNull().orEmpty()
        val isVip = prefs.getBoolean(KEY_IS_VIP, false)
        val unlocked = prefs.getStringSet(KEY_UNLOCKED_ITEMS, emptySet()).orEmpty().toList()

        _currentUserRecord.value = _currentUserRecord.value.copy(
            uid = currentUid ?: auth?.currentUser?.uid.orEmpty(),
            displayName = "User • $code",
            deviceId = devId,
            deviceModel = deviceLabel(),
            appVersion = BuildConfig.VERSION_NAME,
            shareCode = code,
            points = pts,
            shareCount = shares,
            redeemedCode = redeemedSingle,
            redeemedCodes = redeemedSet,
            isVip = isVip,
            unlockedItemIds = unlocked,
            online = foreground
        )
    }

    private fun saveLocalState(
        points: Int = _currentUserRecord.value.points,
        shareCount: Int = _currentUserRecord.value.shareCount,
        redeemedCode: String = _currentUserRecord.value.redeemedCode,
        redeemedCodes: List<String> = _currentUserRecord.value.redeemedCodes,
        isVip: Boolean = _currentUserRecord.value.isVip,
        unlockedItemIds: List<String> = _currentUserRecord.value.unlockedItemIds
    ) {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_POINTS, points.coerceAtLeast(0))
            .putInt(KEY_SHARE_COUNT, shareCount.coerceAtLeast(0))
            .putString(KEY_REDEEMED_CODE, redeemedCode)
            .putStringSet(KEY_REDEEMED_CODES_SET, redeemedCodes.toSet())
            .putBoolean(KEY_IS_VIP, isVip)
            .putStringSet(KEY_UNLOCKED_ITEMS, unlockedItemIds.toSet())
            .apply()

        _currentUserRecord.value = _currentUserRecord.value.copy(
            points = points.coerceAtLeast(0),
            shareCount = shareCount.coerceAtLeast(0),
            redeemedCode = redeemedCode,
            redeemedCodes = redeemedCodes.distinct(),
            isVip = isVip,
            unlockedItemIds = unlockedItemIds.distinct()
        )
    }

    /** Called from MainActivity.onStart / onStop. */
    fun setForeground(isForeground: Boolean) {
        foreground = isForeground
        _currentUserRecord.value = _currentUserRecord.value.copy(online = isForeground)
        val ref = currentRef ?: return
        runCatching {
            ref.updateChildren(
                mapOf(
                    "online" to isForeground,
                    "lastSeen" to ServerValue.TIMESTAMP
                )
            )
        }
    }

    fun updateFcmToken(token: String) {
        if (token.isBlank()) return
        _currentUserRecord.value = _currentUserRecord.value.copy(fcmToken = token)
        val ref = currentRef ?: return
        runCatching { ref.child("fcmToken").setValue(token) }
    }

    private data class VerifiedFirebaseCodeOwner(
        val shareCode: String,
        val ownerUid: String,
        val ownerDeviceId: String,
        val existingShareCount: Int,
        val existingBonusPoints: Int
    )

    /**
     * Redeems a friend's 5-digit Share Code ONLY after verifying that the 5-digit code
     * actually exists in Firebase Realtime Database (`/shareCodes/{code}`, `/users`, or
     * `/config/registeredCodes/{code}`) and belongs to a different device/user.
     *
     * Random 5-digit codes that are not registered in Firebase are strictly rejected!
     */
    fun redeemShareCode(
        rawCode: String,
        pointsPerReferral: Int,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val code = rawCode.trim()
        if (code.length != 5 || !code.all { it.isDigit() }) {
            onResult(false, "Please enter a valid 5-digit Share Code.")
            return
        }
        val cur = _currentUserRecord.value
        val myCode = cur.shareCode.ifBlank { getFiveDigitShareCode() }
        val myDeviceId = cur.deviceId.ifBlank { getRealDeviceId() }

        if (code == myCode) {
            onResult(false, "You cannot redeem your own 5-digit Share Code ($myCode).")
            return
        }
        if (code in cur.redeemedCodes || code == cur.redeemedCode) {
            onResult(false, "Share Code $code has already been redeemed on this device.")
            return
        }
        val database = db ?: run {
            onResult(false, "Firebase Database unavailable. Check your internet connection.")
            return
        }
        if (isRedeemInProgress) {
            onResult(false, "Verifying code with Firebase, please wait...")
            return
        }

        isRedeemInProgress = true
        scope.launch {
            try {
                val verifiedOwner = withContext(Dispatchers.IO) {
                    findRegisteredCodeInFirebase(
                        database = database,
                        code = code
                    )
                }

                if (verifiedOwner == null) {
                    isRedeemInProgress = false
                    onResult(
                        false,
                        "Invalid Share Code ($code)! Only a real 5-digit code registered in Firebase can be redeemed."
                    )
                    return@launch
                }

                // Ensure the code in Firebase does not belong to this same physical device
                if ((verifiedOwner.ownerDeviceId.isNotBlank() && verifiedOwner.ownerDeviceId == myDeviceId) ||
                    (verifiedOwner.ownerUid.isNotBlank() && verifiedOwner.ownerUid == cur.uid && verifiedOwner.ownerDeviceId.isBlank())
                ) {
                    isRedeemInProgress = false
                    onResult(false, "You cannot redeem your own device's 5-digit Share Code ($code).")
                    return@launch
                }

                // Code is verified in Firebase! Award points and increment shareCount for both users
                val reward = pointsPerReferral.coerceAtLeast(1)
                val latestCur = _currentUserRecord.value
                val updatedRedeemedList = (latestCur.redeemedCodes + code).distinct()
                val newPoints = latestCur.points + reward
                val newShareCount = latestCur.shareCount + 1

                saveLocalState(
                    points = newPoints,
                    shareCount = newShareCount,
                    redeemedCode = code,
                    redeemedCodes = updatedRedeemedList
                )
                syncCurrentRecordToFirebase()

                // Credit the friend (code owner) in Firebase (/shareCodes/{code} and /users/{ownerUid})
                withContext(Dispatchers.IO) {
                    runCatching {
                        val targetCodeRef = database.getReference("shareCodes").child(code)
                        val codeUpdates = HashMap<String, Any?>()
                        codeUpdates["shareCode"] = code
                        if (verifiedOwner.ownerUid.isNotBlank()) {
                            codeUpdates["uid"] = verifiedOwner.ownerUid
                        }
                        if (verifiedOwner.ownerDeviceId.isNotBlank()) {
                            codeUpdates["deviceId"] = verifiedOwner.ownerDeviceId
                        }
                        codeUpdates["shareCount"] = verifiedOwner.existingShareCount + 1
                        codeUpdates["bonusPoints"] = verifiedOwner.existingBonusPoints + reward
                        codeUpdates["lastRedeemedByDeviceId"] = myDeviceId
                        codeUpdates["lastRedeemedAt"] = ServerValue.TIMESTAMP
                        targetCodeRef.updateChildren(codeUpdates).await()
                    }

                    if (verifiedOwner.ownerUid.isNotBlank() && verifiedOwner.ownerUid != latestCur.uid) {
                        runCatching {
                            val ownerUserRef = database.getReference("users").child(verifiedOwner.ownerUid)
                            val userSnap = ownerUserRef.get().await()
                            if (userSnap.exists()) {
                                val uPts = (userSnap.child("points").getValue(Long::class.java)?.toInt()
                                    ?: userSnap.child("points").getValue(Int::class.java) ?: 0) + reward
                                val uShares = (userSnap.child("shareCount").getValue(Long::class.java)?.toInt()
                                    ?: userSnap.child("shareCount").getValue(Int::class.java) ?: 0) + 1
                                ownerUserRef.updateChildren(
                                    mapOf(
                                        "points" to uPts,
                                        "shareCount" to uShares,
                                        "lastSeen" to ServerValue.TIMESTAMP
                                    )
                                ).await()
                            }
                        }
                    }
                }

                isRedeemInProgress = false
                onResult(true, "Verified Firebase Code $code redeemed! +$reward VIP Points added (Total: $newPoints Pts).")
            } catch (e: Exception) {
                isRedeemInProgress = false
                onResult(false, "Could not verify Share Code $code with Firebase. Please check internet connection.")
            }
        }
    }

    /**
     * Checks Firebase Realtime Database to verify that `code` was actually created/registered
     * by a real MovieBox device:
     * 1. Checks `/shareCodes/{code}`
     * 2. Checks `/config/registeredCodes/{code}` (synced by Admin Panel)
     * 3. Checks `/users` for any registered user whose `shareCode == code`
     */
    private suspend fun findRegisteredCodeInFirebase(
        database: FirebaseDatabase,
        code: String
    ): VerifiedFirebaseCodeOwner? {
        // 1. Check /shareCodes/{code}
        runCatching {
            val snap = database.getReference("shareCodes").child(code).get().await()
            if (snap.exists()) {
                val ownerUid = snap.child("uid").getValue(String::class.java).orEmpty()
                val ownerDevId = snap.child("deviceId").getValue(String::class.java).orEmpty()
                val snapCode = snap.child("shareCode").getValue(String::class.java).orEmpty()
                if (ownerUid.isNotBlank() || ownerDevId.isNotBlank() || snapCode == code) {
                    val shares = snap.child("shareCount").getValue(Long::class.java)?.toInt()
                        ?: snap.child("shareCount").getValue(Int::class.java) ?: 0
                    val bonus = snap.child("bonusPoints").getValue(Long::class.java)?.toInt()
                        ?: snap.child("bonusPoints").getValue(Int::class.java) ?: 0
                    return VerifiedFirebaseCodeOwner(
                        shareCode = code,
                        ownerUid = ownerUid,
                        ownerDeviceId = ownerDevId,
                        existingShareCount = shares,
                        existingBonusPoints = bonus
                    )
                }
            }
        }

        // 2. Check /config/registeredCodes/{code}
        runCatching {
            val regSnap = database.getReference("config").child("registeredCodes").child(code).get().await()
            if (regSnap.exists()) {
                val ownerUid = regSnap.child("uid").getValue(String::class.java).orEmpty()
                val ownerDevId = regSnap.child("deviceId").getValue(String::class.java).orEmpty()
                if (ownerUid.isNotBlank() || ownerDevId.isNotBlank()) {
                    return VerifiedFirebaseCodeOwner(
                        shareCode = code,
                        ownerUid = ownerUid,
                        ownerDeviceId = ownerDevId,
                        existingShareCount = 0,
                        existingBonusPoints = 0
                    )
                }
            }
        }

        // 3. Check /users for any user record with shareCode == code
        runCatching {
            val usersSnap = database.getReference("users").get().await()
            if (usersSnap.exists()) {
                for (child in usersSnap.children) {
                    val rec = UserPresenceRecord.fromSnapshot(child) ?: continue
                    if (rec.shareCode == code) {
                        return VerifiedFirebaseCodeOwner(
                            shareCode = code,
                            ownerUid = rec.uid,
                            ownerDeviceId = rec.deviceId,
                            existingShareCount = rec.shareCount,
                            existingBonusPoints = rec.points
                        )
                    }
                }
            }
        }

        return null
    }

    fun markItemUnlocked(itemId: String) {
        if (itemId.isBlank()) return
        val cur = _currentUserRecord.value
        if (itemId in cur.unlockedItemIds) return
        val updated = (cur.unlockedItemIds + itemId).distinct()
        saveLocalState(unlockedItemIds = updated)
        syncCurrentRecordToFirebase()
    }

    private fun signInAnonymouslyOnce(fa: FirebaseAuth) {
        if (anonymousAttempted) return
        anonymousAttempted = true
        runCatching {
            fa.signInAnonymously().addOnCompleteListener { task ->
                if (!task.isSuccessful) anonymousAttempted = false
            }
        }.onFailure { anonymousAttempted = false }
    }

    private fun attachDeviceOverrideListener() {
        val database = db ?: return
        val devId = getRealDeviceId()
        if (devId.isBlank()) return
        runCatching { overrideListener?.let { overrideRef?.removeEventListener(it) } }
        val ref = database.getReference("config").child("userOverrides").child(devId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val ctx = appContext ?: return
                val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val updatedAt = snapshot.child("updatedAt").getValue(Long::class.java) ?: 0L
                val lastSynced = prefs.getLong(KEY_LAST_SYNCED_OVERRIDE_TS, 0L)

                val overridePoints = snapshot.child("points").getValue(Long::class.java)?.toInt()
                    ?: snapshot.child("points").getValue(Int::class.java)
                val overrideShares = snapshot.child("shareCount").getValue(Long::class.java)?.toInt()
                    ?: snapshot.child("shareCount").getValue(Int::class.java)
                val overrideVip = snapshot.child("isVip").getValue(Boolean::class.java)

                if (updatedAt > lastSynced || overrideVip != null) {
                    val cur = _currentUserRecord.value
                    val nextPts = overridePoints ?: cur.points
                    val nextShares = overrideShares ?: cur.shareCount
                    val nextVip = overrideVip ?: cur.isVip
                    prefs.edit().putLong(KEY_LAST_SYNCED_OVERRIDE_TS, updatedAt).apply()
                    saveLocalState(
                        points = nextPts,
                        shareCount = nextShares,
                        isVip = nextVip
                    )
                    syncCurrentRecordToFirebase()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        overrideRef = ref
        overrideListener = listener
        runCatching { ref.addValueEventListener(listener) }
    }

    private fun attachShareCodeReferralListener() {
        val database = db ?: return
        val myCode = getFiveDigitShareCode()
        if (myCode.isBlank()) return
        runCatching { shareCodeListener?.let { shareCodeRef?.removeEventListener(it) } }
        val ref = database.getReference("shareCodes").child(myCode)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val ctx = appContext ?: return
                val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val remoteReferrals = snapshot.child("shareCount").getValue(Long::class.java)?.toInt()
                    ?: snapshot.child("shareCount").getValue(Int::class.java) ?: 0
                val remoteBonusPts = snapshot.child("bonusPoints").getValue(Long::class.java)?.toInt()
                    ?: snapshot.child("bonusPoints").getValue(Int::class.java) ?: 0
                val lastSyncedReferrals = prefs.getInt(KEY_LAST_SYNCED_REFERRAL_COUNT, 0)

                if (remoteReferrals > lastSyncedReferrals) {
                    val deltaReferrals = remoteReferrals - lastSyncedReferrals
                    val cur = _currentUserRecord.value
                    val addedPts = if (remoteBonusPts > 0) (deltaReferrals * (remoteBonusPts / remoteReferrals.coerceAtLeast(1))) else (deltaReferrals * 50)
                    prefs.edit().putInt(KEY_LAST_SYNCED_REFERRAL_COUNT, remoteReferrals).apply()
                    saveLocalState(
                        points = cur.points + addedPts.coerceAtLeast(0),
                        shareCount = cur.shareCount + deltaReferrals
                    )
                    syncCurrentRecordToFirebase()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        shareCodeRef = ref
        shareCodeListener = listener
        runCatching { ref.addValueEventListener(listener) }
    }

    private fun attachPresence(user: FirebaseUser) {
        val database = db ?: return
        if (currentUid == user.uid && currentRef != null) return
        detachPresence()
        anonymousAttempted = false

        val ref = database.reference.child("users").child(user.uid)
        currentUid = user.uid
        currentRef = ref

        val devId = getRealDeviceId()
        val myCode = getFiveDigitShareCode()
        val email = user.email.orEmpty()
        val displayName = when {
            !user.displayName.isNullOrBlank() -> user.displayName!!
            email.isNotBlank() -> email.substringBefore('@')
            else -> "User • $myCode (${deviceLabel()})"
        }

        _currentUserRecord.value = _currentUserRecord.value.copy(
            uid = user.uid,
            displayName = displayName,
            email = email,
            deviceId = devId,
            deviceModel = deviceLabel(),
            shareCode = myCode
        )

        // Immediately register in Firebase /users/{uid} and /shareCodes/{myCode}
        syncCurrentRecordToFirebase()

        val selfListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val remote = UserPresenceRecord.fromSnapshot(snapshot) ?: return
                val local = _currentUserRecord.value
                val mergedPoints = maxOf(local.points, remote.points)
                val mergedShares = maxOf(local.shareCount, remote.shareCount)
                val mergedCodes = (local.redeemedCodes + remote.redeemedCodes).distinct()
                val mergedUnlocked = (local.unlockedItemIds + remote.unlockedItemIds).distinct()
                val mergedVip = local.isVip || remote.isVip

                if (mergedPoints != local.points ||
                    mergedShares != local.shareCount ||
                    mergedVip != local.isVip ||
                    mergedCodes.size != local.redeemedCodes.size
                ) {
                    saveLocalState(
                        points = mergedPoints,
                        shareCount = mergedShares,
                        redeemedCode = local.redeemedCode.ifBlank { remote.redeemedCode },
                        redeemedCodes = mergedCodes,
                        isVip = mergedVip,
                        unlockedItemIds = mergedUnlocked
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        userSelfListener = selfListener
        runCatching { ref.addValueEventListener(selfListener) }

        val conn = database.getReference(".info/connected")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) != true) return
                runCatching {
                    ref.child("online").onDisconnect().setValue(false)
                    ref.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)
                    syncCurrentRecordToFirebase()
                }
                fetchAndStoreFcmToken(ref)
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        connectedRef = conn
        connectedListener = listener
        conn.addValueEventListener(listener)
    }

    fun syncCurrentRecordToFirebase() {
        val ref = currentRef ?: return
        val cur = _currentUserRecord.value
        val uid = currentUid ?: cur.uid
        if (uid.isBlank()) return
        val devId = getRealDeviceId()
        val myCode = getFiveDigitShareCode()
        val email = auth?.currentUser?.email.orEmpty()
        val displayName = when {
            !auth?.currentUser?.displayName.isNullOrBlank() -> auth?.currentUser?.displayName!!
            email.isNotBlank() -> email.substringBefore('@')
            else -> "User • $myCode (${deviceLabel()})"
        }

        runCatching {
            ref.updateChildren(
                mapOf(
                    "uid" to uid,
                    "displayName" to displayName,
                    "email" to email,
                    "deviceId" to devId,
                    "deviceModel" to deviceLabel(),
                    "shareCode" to myCode,
                    "points" to cur.points,
                    "shareCount" to cur.shareCount,
                    "redeemedCode" to cur.redeemedCode,
                    "redeemedCodes" to cur.redeemedCodes,
                    "isVip" to cur.isVip,
                    "unlockedItemIds" to cur.unlockedItemIds,
                    "appVersion" to BuildConfig.VERSION_NAME,
                    "isAnonymous" to (auth?.currentUser?.isAnonymous ?: true),
                    "online" to foreground,
                    "lastSeen" to ServerValue.TIMESTAMP
                )
            )
        }

        if (devId.equals(OWNER_ADMIN_DEVICE_ID, ignoreCase = true)) {
            runCatching {
                db?.getReference("adminDevices")?.child(OWNER_ADMIN_DEVICE_ID)?.setValue(uid)
            }
        }

        runCatching {
            db?.getReference("shareCodes")?.child(myCode)?.updateChildren(
                mapOf(
                    "shareCode" to myCode,
                    "uid" to uid,
                    "deviceId" to devId,
                    "deviceModel" to deviceLabel(),
                    "updatedAt" to ServerValue.TIMESTAMP
                )
            )
        }
    }

    private fun detachPresence() {
        runCatching {
            connectedListener?.let { connectedRef?.removeEventListener(it) }
            userSelfListener?.let { currentRef?.removeEventListener(it) }
        }
        connectedListener = null
        connectedRef = null
        userSelfListener = null
        currentRef = null
        currentUid = null
    }

    private fun fetchAndStoreFcmToken(ref: DatabaseReference) {
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) {
                    _currentUserRecord.value = _currentUserRecord.value.copy(fcmToken = token)
                    runCatching { ref.child("fcmToken").setValue(token) }
                }
            }
        }
    }

    private fun deviceLabel(): String {
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.titlecase(Locale.US) }
        val model = Build.MODEL.orEmpty()
        return if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim()
    }
}
