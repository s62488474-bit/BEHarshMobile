-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.beharsh.mobile.**$$serializer { *; }
-keepclassmembers class com.beharsh.mobile.** { *** Companion; }
-keepclasseswithmembers class com.beharsh.mobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.beharsh.mobile.admin.** { *; }
-keep class com.beharsh.mobile.receiver.** { *; }
-keep class com.beharsh.mobile.service.** { *; }
-keep public class com.google.zxing.** { *; }
