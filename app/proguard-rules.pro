# Proguard rules for Ambiorix Pay
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
# Keep notification listener service
-keep class com.ambiorix.pay.notification.AmbiorixNotificationListener { *; }
