-keepattributes *Annotation*, Signature
-keepclassmembers class kotlinx.serialization.** { *** Companion; }
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.bizbite.printer.data.remote.dto.** { *; }
-keep class com.bizbite.printer.data.remote.** { *; }
-keep class com.bizbite.printer.data.local.** { *; }
-keep class com.bizbite.printer.domain.model.PrinterConfig { *; }
-keep class com.bizbite.printer.printer.BluetoothSppConnection { *; }
-keep class com.bizbite.printer.printer.BluetoothLeConnection { *; }
-keep class com.bizbite.printer.printer.EscPosBuilder { *; }
-keep class com.bizbite.printer.printer.ReceiptRenderer { *; }
-dontwarn javax.annotation.**
