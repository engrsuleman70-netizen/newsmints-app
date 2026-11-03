# Proguard rules for Newsmints

# AdMob Proguard rules
-keep public class com.google.android.gms.ads.** {
   public *;
}

# AndroidX / Jetpack Compose
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>();
}

-dontwarn com.google.android.gms.**
-dontwarn androidx.**
