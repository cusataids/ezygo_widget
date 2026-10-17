# kotlinx.serialization keeps generated serializers reachable through reflection
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ezygo.attendance.** {
    *** Companion;
}
-keepclasseswithmembers class com.ezygo.attendance.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ezygo.attendance.**$$serializer { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions

# Glance widgets. Inspecting the release mapping showed these already surviving via
# the libraries' own consumer rules, so this is insurance rather than a fix: all
# three are reached reflectively or from the manifest, which is exactly the shape
# R8 is most likely to get wrong after a version bump.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * implements androidx.glance.appwidget.action.ActionCallback { *; }

# Hilt entry points are looked up by interface, including from broadcast receivers
# that cannot be injected directly.
-keep @dagger.hilt.EntryPoint interface * { *; }

# PdfBox reaches for a JPEG-2000 decoder that ships as a separate artifact
# (com.gemalto.jp2:jp2-android) and is not a dependency here. Its only caller is
# JPXFilter, which decodes JP2 images embedded in a PDF — a path this app never
# takes: the grade cards are read for text, and drawn by Android's own PdfRenderer.
# The class really is absent, so this states that the absence is deliberate rather
# than pulling in a decoder for images that are never decoded.
-dontwarn com.gemalto.jp2.**
