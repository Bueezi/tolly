# kotlinx.serialization: keep generated serializers of our DTOs and stored session.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class be.tolly.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class be.tolly.**$$serializer { *; }

# Retrofit service interfaces are created reflectively.
-keep,allowobfuscation interface be.tolly.core.network.UltraApi
