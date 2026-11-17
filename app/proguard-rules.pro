-keepattributes *Annotation*, Signature
-keepclassmembers class kotlinx.serialization.** { *** Companion; }
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.bizbite.printer.data.remote.dto.** { *; }
-dontwarn javax.annotation.**
