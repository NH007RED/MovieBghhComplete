package com.moviebox.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.moviebox.app.MainActivity
import com.moviebox.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.TimeUnit

/**
 * Holds parsed Google Service Account JSON credentials strictly in runtime memory.
 * Never persisted or bundled inside the application project.
 */
data class ServiceAccountCredentials(
    val projectId: String,
    val clientEmail: String,
    val privateKeyPem: String,
    val tokenUri: String = "https://oauth2.googleapis.com/token"
)

object FcmPushSender {
    private const val CHANNEL_ID = "moviebox_push_alerts"
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Validates a Google Cloud / Firebase Service Account JSON string uploaded by the admin.
     */
    fun parseAndValidateServiceAccountJson(rawJson: String): Result<ServiceAccountCredentials> = runCatching {
        val obj = JsonParser.parseString(rawJson.trim()).asJsonObject
        val type = obj.get("type")?.asString.orEmpty()
        require(type == "service_account") {
            "Invalid JSON: 'type' must be 'service_account' (found '$type')."
        }
        val projectId = obj.get("project_id")?.asString?.trim().orEmpty()
        val clientEmail = obj.get("client_email")?.asString?.trim().orEmpty()
        val privateKey = obj.get("private_key")?.asString?.trim().orEmpty()
        val tokenUri = obj.get("token_uri")?.asString?.trim().orEmpty()
            .ifBlank { "https://oauth2.googleapis.com/token" }

        require(projectId.isNotBlank()) { "Service Account JSON is missing 'project_id'." }
        require(clientEmail.contains("@")) { "Service Account JSON is missing a valid 'client_email'." }
        require(privateKey.length > 64) { "Service Account JSON is missing a valid key field." }

        // Test RSA key parsing immediately so invalid keys are caught on upload
        parseRsaPrivateKey(privateKey)

        ServiceAccountCredentials(
            projectId = projectId,
            clientEmail = clientEmail,
            privateKeyPem = privateKey,
            tokenUri = tokenUri
        )
    }

    private fun parseRsaPrivateKey(pem: String) = run {
        val clean = pem
            .replace("\\n", "\n")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("--") }
            .joinToString("")
        val decoded = Base64.decode(clean, Base64.DEFAULT)
        val spec = PKCS8EncodedKeySpec(decoded)
        KeyFactory.getInstance("RSA").generatePrivate(spec)
    }

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun fetchAccessToken(creds: ServiceAccountCredentials): String {
        val nowSec = System.currentTimeMillis() / 1000L
        val expSec = nowSec + 3600L

        val headerJson = JsonObject().apply {
            addProperty("alg", "RS256")
            addProperty("typ", "JWT")
        }.toString()
        val claimJson = JsonObject().apply {
            addProperty("iss", creds.clientEmail)
            addProperty("scope", "https://www.googleapis.com/auth/firebase.messaging")
            addProperty("aud", creds.tokenUri)
            addProperty("iat", nowSec)
            addProperty("exp", expSec)
        }.toString()

        val unsignedToken = "${base64UrlEncode(headerJson.toByteArray())}.${base64UrlEncode(claimJson.toByteArray())}"
        val privateKey = parseRsaPrivateKey(creds.privateKeyPem)
        val signer = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(unsignedToken.toByteArray(Charsets.UTF_8))
        }
        val signature = base64UrlEncode(signer.sign())
        val jwt = "$unsignedToken.$signature"

        val formBody = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=$jwt"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())

        val req = Request.Builder()
            .url(creds.tokenUri)
            .post(formBody)
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("OAuth2 token exchange failed (HTTP ${resp.code}): $bodyStr")
            }
            val json = JsonParser.parseString(bodyStr).asJsonObject
            return json.get("access_token")?.asString
                ?: error("OAuth2 response missing access_token")
        }
    }

    /**
     * Sends an FCM HTTP v1 push notification to topic 'moviebox_all' AND any individual user FCM tokens.
     */
    suspend fun sendPushNotificationHttpV1(
        creds: ServiceAccountCredentials,
        title: String,
        body: String,
        imageUrl: String,
        itemId: String,
        userTokens: List<String>
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val accessToken = fetchAccessToken(creds)
            val endpoint = "https://fcm.googleapis.com/v1/projects/${creds.projectId}/messages:send"
            var successCount = 0

            // 1. Send to topic "moviebox_all" so all subscribed devices receive it
            val topicMessage = buildFcmMessageJson(
                targetKey = "topic",
                targetValue = "moviebox_all",
                title = title,
                body = body,
                imageUrl = imageUrl,
                itemId = itemId
            )
            val topicReq = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .post(topicMessage.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(topicReq).execute().use { resp ->
                if (resp.isSuccessful) successCount++
            }

            // 2. Also send to individual user FCM tokens if registered in /users
            userTokens.filter { it.isNotBlank() }.distinct().forEach { token ->
                runCatching {
                    val tokenMsg = buildFcmMessageJson(
                        targetKey = "token",
                        targetValue = token,
                        title = title,
                        body = body,
                        imageUrl = imageUrl,
                        itemId = itemId
                    )
                    val req = Request.Builder()
                        .url(endpoint)
                        .addHeader("Authorization", "Bearer $accessToken")
                        .addHeader("Content-Type", "application/json; charset=UTF-8")
                        .post(tokenMsg.toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) successCount++
                    }
                }
            }
            successCount
        }
    }

    private fun buildFcmMessageJson(
        targetKey: String,
        targetValue: String,
        title: String,
        body: String,
        imageUrl: String,
        itemId: String
    ): String {
        val notificationObj = JsonObject().apply {
            addProperty("title", title)
            addProperty("body", body)
            if (imageUrl.isNotBlank()) {
                addProperty("image", imageUrl)
            }
        }
        val dataObj = JsonObject().apply {
            addProperty("title", title)
            addProperty("body", body)
            addProperty("imageUrl", imageUrl)
            addProperty("itemId", itemId)
        }
        val msgObj = JsonObject().apply {
            addProperty(targetKey, targetValue)
            add("notification", notificationObj)
            add("data", dataObj)
        }
        return JsonObject().apply { add("message", msgObj) }.toString()
    }

    fun subscribeAppToAllUsersTopic() {
        runCatching {
            FirebaseMessaging.getInstance().subscribeToTopic("moviebox_all")
        }
    }

    suspend fun showSystemNotification(
        context: Context,
        title: String,
        body: String,
        imageUrl: String
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "MovieBox New Releases & Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for new movies, web series, and updates"
                }
                nm.createNotificationChannel(ch)
            }

            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return@runCatching
            }

            val bitmap: Bitmap? = if (imageUrl.isNotBlank()) {
                runCatching {
                    URL(imageUrl).openStream().use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            } else null

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                (System.currentTimeMillis() % 10000).toInt(),
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText(body)
                )
            } else {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
            }

            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
        }
    }
}

class MovieBoxMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmPushSender.subscribeAppToAllUsersTopic()
        runCatching { com.moviebox.app.data.repository.UserPresenceManager.updateFcmToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "MovieBox"
        val body = message.notification?.body ?: message.data["body"] ?: "New content available!"
        val img = message.notification?.imageUrl?.toString() ?: message.data["imageUrl"].orEmpty()
        CoroutineScope(Dispatchers.IO).launch {
            FcmPushSender.showSystemNotification(applicationContext, title, body, img)
        }
    }
}
