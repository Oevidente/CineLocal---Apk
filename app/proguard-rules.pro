# Regras de ProGuard / R8 para CineLocal (QA-025)

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Room Database
-keepclassmembers class * extends androidx.room.RoomDatabase {
    static <fields>;
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class okhttp3.internal.publicsuffix.PublicSuffixDatabase {
    *;
}

# Google Cast
-keep class com.google.android.gms.cast.** { *; }
-keep class com.example.cinelocal.cast.CastOptionsProvider { *; }

# SMBJ
-keep class com.hierynomus.** { *; }
-dontwarn com.hierynomus.**
-dontwarn org.bouncycastle.**

# TorrentStream
-keep class com.github.se_bastiaan.torrentstream.** { *; }
-dontwarn com.github.se_bastiaan.torrentstream.**

# Data Models
-keep class com.example.cinelocal.data.model.** { *; }
-keepclassmembers class com.example.cinelocal.data.model.** { *; }
