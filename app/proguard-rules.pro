# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Google Play Core Libraries (App Update, Review, Age Signals) ---
-keep class com.google.android.play.** { *; }
-dontwarn com.google.android.play.**

# --- User Messaging Platform (UMP Consent) ---
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

# --- Google Mobile Ads (AdMob) ---
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# --- App Specific Models & Helper Handlers ---
-keep class com.doodlr.aeslocker.AppReviewHelper { *; }
-keep class com.doodlr.aeslocker.AppUpdateHelper { *; }
-keep class com.doodlr.aeslocker.ConsentAndAgeManager { *; }
-keep class com.doodlr.aeslocker.OpenSSLCrypto { *; }