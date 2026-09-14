# Keep app classes
-keep class com.mms.minzmahallu.** { *; }

# Compose / Kotlin – required when minify is re-enabled
-keep class androidx.compose.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.** { *; }

# SQLite / Room / androidX sqlite
-keep class androidx.sqlite.** { *; }
-keep class android.database.sqlite.** { *; }

# PDF / QR / BouncyCastle
-dontwarn com.tom_roush.**
-keep class com.tom_roush.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.apache.**
-keep class org.apache.pdfbox.** { *; }
-dontwarn javax.**
-dontwarn java.awt.**
-keep class com.google.zxing.** { *; }

# Desugar
-dontwarn java.lang.invoke.**

-ignorewarnings
-dontoptimize
