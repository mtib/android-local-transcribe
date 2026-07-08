# sherpa-onnx JNI: the native layer instantiates these Kotlin data classes by field, keep them.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# kotlinx.serialization — keep generated serializers and Companion accessors.
-keepclassmembers class dev.mtib.localtranscribe.** {
    *** Companion;
}
-keepclasseswithmembers class dev.mtib.localtranscribe.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.mtib.localtranscribe.**$$serializer { *; }
