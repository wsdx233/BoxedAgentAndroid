# Keep kotlinx serialization metadata for release builds.
-keepattributes *Annotation*, InnerClasses
-keep class com.boxedagent.android.data.**$$serializer { *; }
-keepclassmembers class com.boxedagent.android.data.** {
    public static ** Companion;
}
-dontwarn kotlinx.serialization.**
