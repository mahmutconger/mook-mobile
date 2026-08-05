# ============================================================
# Mook — Production ProGuard / R8 Rules
# ============================================================
# Libraries like Coil 3.x and Koin ship their own consumer rules;
# only add rules here when a release-build crash confirms a class
# is incorrectly removed.

# ---- Kotlin & Kotlin Multiplatform --------------------------
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
-keep class kotlin.Metadata { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ---- Kotlinx Serialization ----------------------------------
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class kotlinx.serialization.** { *; }

# ---- Firebase (gitlive KMP wrapper + underlying Google SDK) --
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}
-keep public class com.google.firebase.provider.FirebaseInitProvider
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class dev.gitlive.firebase.** { *; }
-dontwarn dev.gitlive.firebase.**

# ---- Koin ---------------------------------------------------
-keep class io.insert-koin.** { *; }
-dontwarn io.insert-koin.**
-keepnames class com.mcclabs.mook.** { *; }

# ---- Coil 3.x -----------------------------------------------
-dontwarn coil.**

# ---- Ktor / OkHttp / Okio ----------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- Jetpack Compose ----------------------------------------
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# ---- General Android patterns --------------------------------
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
