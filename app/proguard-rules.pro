# OptiPause ProGuard rules
# ─────────────────────────────────────────────────────────────────────────────

# Keep all BroadcastReceivers so AlarmManager can instantiate them by class name
-keep public class com.jptechgenius.optipause.receiver.** { *; }

# Keep all Services
-keep public class com.jptechgenius.optipause.service.** { *; }

# Keep ViewModel classes (accessed via ViewModelProvider reflection)
-keep public class com.jptechgenius.optipause.viewmodel.** { *; }

# Keep Repository (accessed from ViewModel and Receivers)
-keep public class com.jptechgenius.optipause.repository.** { *; }

# Keep Application class
-keep public class com.jptechgenius.optipause.OptiPauseApplication { *; }

# AndroidX & Material — standard keep rules (included transitively, listed for clarity)
-keep class androidx.lifecycle.** { *; }
-keep class com.google.android.material.** { *; }

# Suppress warnings for missing classes in release builds
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
