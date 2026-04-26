# Keep kotlinx serialization metadata for release builds if minify is enabled later.
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
