# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/snead/Android/Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

#-verbose

-keep class com.loafofpiecrust.turntable.** { *; }

# Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.loafofpiecrust.turntable.**$$serializer { *; }
-keepclassmembers class com.loafofpiecrust.turntable.** {
    *** Companion;
}
-keepclasseswithmembers class com.loafofpiecrust.turntable.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable
#-keep com.sothree.slidinguppanel.*
#-keep class com.loafofpiecrust.turntable.** { *; }
#-keep class com.loafofpiecrust.turntable.**$** { *; }
#-keep class com.loafofpiecrust.turntable.ui.*$** { *; }
-keep class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.loafofpiecrust.**$Parcelable { *; }
#-keep class com.frostwire.jlibtorrent.swig.** { *; }
#-keep class com.google.firebase.provider.FirebaseInitProvider
#-keep class * extends android.content.ContentProvider
-keep class com.loafofpiecrust.turntable.R
# If you keep the line number information, uncomment this to
# hide the original source file uuid.
#-renamesourcefileattribute SourceFile
-dontnote android.**
-dontnote dalvik.**
-dontnote com.android.**
-dontnote google.**
-dontnote com.google.**
-dontnote java.**
-dontnote javax.**
-dontnote junit.**
-dontnote org.apache.**
-dontnote org.json.**
-dontnote org.w3c.dom.**
-dontnote org.xml.sax.**
-dontnote org.xmlpull.v1.**

-dontwarn android.**
-dontwarn dalvik.**
-dontwarn com.android.**
-dontwarn google.**
-dontwarn com.google.**
-dontwarn java.**
-dontwarn javax.**
-dontwarn junit.**
-dontwarn org.apache.**
-dontwarn org.json.**
-dontwarn org.w3c.dom.**
-dontwarn org.xml.sax.**
-dontwarn org.xmlpull.v1.**
-dontwarn org.slf4j.**
-dontwarn kotlin.**
-dontwarn com.loafofpiecrust.**

-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

# Some stuff needs explicit keeping, >_>
#-keep class com.mikepenz.materialize.view.OnInsetsCallback
#-keep class com.sothree.slidinguppanel.ScrollableViewHelper
#-keep class com.sothree.slidinguppanel.SlidingUpPanelLayout$*
#-keep class org.jsoup.parser.HtmlTreeBuilderState$1
#-keep class org.jsoup.parser.TokeniserState$1
#-keep class com.bumptech.glide.load.engine.executor.GlideExecutor$1
#-keep class kotlin.jvm.internal.DefaultConstructorMarker
#-keep class kotlin.reflect.jvm.internal.ReflectionFactoryImpl
#-keep class kotlin.reflect.jvm.internal.impl.builtins.**
#-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
#-keep class com.chibatching.kotpref.** { *; }
#-keep class java.math.** { *; }
#-keep class java.time.** { *; }
#-keep class java.beans.** { *; }
#-keep class org.jdom.xpath.** { *; }
-keep class com.esotericsoftware.kryo.serializers.* { *; }
#-keepclassmembers class java.lang.Class { *; }

-ignorewarnings

# Keep Options
#-keep public class * extends android.app.Activity
#-keep public class com.loafofpiecrust.** extends android.support.v7.app.FragmentActivity
#-keep public class com.loafofpiecrust.** extends android.app.Application
#-keep public class com.loafofpiecrust.** extends android.app.Service
#-keep public class com.loafofpiecrust.** extends android.content.BroadcastReceiver
#-keep public class * extends android.content.ContentProvider
#-keep public class * extends android.app.backup.BackupAgentHelper
#-keep public class * extends android.preference.Preference
#-keep public class com.google.vending.licensing.ILicensingService
#-keep public class com.android.vending.licensing.ILicensingService
#-keep class com.google.android.gms.dynamite.DynamiteModule$DynamiteLoaderClassLoader { *; }
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
#-keep class kotlin.reflect.jvm.internal.ReflectionFactoryImpl
#-keep class com.google.firebase.auth.FirebaseAuth
#-keep class com.google.firebase.crash.FirebaseCrash
#-keep class java.nio.file.Path
#-keep class java.util.** { *; }
#-keep class kotlin.collections.** { <init>(...); }
#-keep class com.android.tools.profiler.agent.okhttp.OkHttp3Interceptor { *; }

-keepclassmembers class com.esotericsoftware.kryo.serializers.** { *; }

-keep class okhttp3.Headers { *; }

#-keepnames class kotlin.reflect.**
#-keepclassmembers class java.util.** { <init>(...); }
#-keepnames class ** { *; }
#-keepnames interface ** { *; }
#-keepnames enum ** { *; }

# Only necessary for XML layouts.
#-keep public class * extends android.view.View {
#    public <init>(android.content.Context);
#    public <init>(android.content.Context, android.util.AttributeSet);
#    public <init>(android.content.Context, android.util.AttributeSet, int);
#    public void set*(...);
#}
-keep class android.support.v7.widget.ListViewCompat

-keepclassmembers class com.loafofpiecrust.** {
    ** serialPersistentFields;
}

#-keepclasseswithmembers class * {
#    public <init>(android.content.Context, android.util.AttributeSet);
#}
#
#-keepclasseswithmembers class * {
#    public <init>(android.content.Context, android.util.AttributeSet, int);
#}

# Only necessary for XML layouts.
#-keepclassmembers class * extends android.content.Context {
#   public void *(android.view.View);
#   public void *(android.view.MenuItem);
#}

-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

#-keepclassmembers class **.R$* {
#    public static <fields>;
#}

#-keepclassmembers class * {
#    @android.webkit.JavascriptInterface <methods>;
#}
#-keepclasseswithmembernames class * {
#    native <methods>;
#}

#-keepclassmembers,allowoptimization enum * {
#    <init>(...);
#    public static **[] values();
#    public static ** valueOf(java.lang.String);
#}

#-keepclassmembers class org.apache.http.message.* {
#    static ** INSTANCE;
#}


#-optimizationpasses 5
# !field/*,!class/merging/*
#-optimizations !code/simplification/arithmetic,!code/allocation/variable,!code/simplification/advanced

# In the optimization step, ProGuard will then remove calls to such methods, if it can determine that the return values aren't used.
#
#-assumenosideeffects class android.util.Log {
#    public static boolean isLoggable(java.lang.String, int);
#    public static int v(...);
#    public static int i(...);
#    public static int w(...);
#    public static int d(...);
#    public static int e(...);
#}

#Specifies that the access modifiers of classes and class members may be broadened during processing.
#Counter-indication: you probably shouldn't use this option when processing code that is to be used as a library, since classes and class members that weren't designed to be public in the API may become public.
-allowaccessmodification

# Obfuscation Options
-dontobfuscate
-dontusemixedcaseclassnames

#For app
-keepattributes Signature
-keepattributes *Annotation*

# Preverification Options
# Preverification is irrelevant for the dex compiler and the Dalvik VM, so we can switch it off with the -dontpreverify option.
-dontpreverify