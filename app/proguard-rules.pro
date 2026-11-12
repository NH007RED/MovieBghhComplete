# ===========================================================================
# MovieBox v24.0 — R8 Obfuscation & Shrinking Rules
# Obfuscates application classes & methods while keeping all JSON / Retrofit /
# Firebase / Media3 / FFmpeg / OkHttp / Finder entry points 100% functional at runtime.
# ===========================================================================
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes *Annotation*

# Keep Gson @SerializedName fields across all DTOs
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Retrofit service interfaces
-keep interface com.moviebox.app.data.remote.TmdbApiService { *; }

# Keep data models and constructors for Gson & Firebase serialization
-keep class com.moviebox.app.data.model.** { *; }
-keep class com.moviebox.app.data.remote.Tmdb* { *; }
-keep class com.moviebox.app.util.OfflineDownloadEntry { *; }
-keep enum com.moviebox.app.util.OfflineDownloadStatus { *; }
-keep class com.moviebox.app.util.ServiceAccountCredentials { *; }
-keep class com.moviebox.app.data.repository.CatalogRepository$CachedSnapshot { *; }
-keep class com.moviebox.app.finder.** { *; }
-keep class com.moviebox.app.net.** { *; }
-keep class com.moviebox.app.player.** { *; }

# Keep Media3 ExoPlayer, OkHttpDataSource & Jellyfin FFmpeg JNI decoder classes
-keep class androidx.media3.** { *; }
-keep class org.jellyfin.media3.** { *; }
-dontwarn androidx.media3.**
-dontwarn org.jellyfin.media3.**

# Keep Android entry points
-keep public class com.moviebox.app.MainActivity
-keep public class com.moviebox.app.MovieBoxApplication
-keep public class com.moviebox.app.util.MovieBoxMessagingService

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
