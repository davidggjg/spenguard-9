# SPENguard ProGuard / R8 rules
#
# Release builds currently run with minifyEnabled=false, but these rules are
# kept correct so shrinking can be switched on without breaking the app.

# Samsung S Pen framework classes referenced reflectively.
-keep class com.samsung.android.** { *; }
-dontwarn com.samsung.android.**

# Components instantiated by the Android framework by name.
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Activity
-keep public class * extends android.app.admin.DeviceAdminReceiver
-keep public class * extends android.accessibilityservice.AccessibilityService

# Keep the app's own entry points explicitly.
-keep class com.davidggjg.spenguard.service.** { *; }
-keep class com.davidggjg.spenguard.receiver.** { *; }
-keep class com.davidggjg.spenguard.ui.** { *; }

# Google Mobile Ads
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.**

# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
