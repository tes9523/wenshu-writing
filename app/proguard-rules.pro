# 保持 kotlinx.serialization 生成的序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.wenshu.writing.**$$serializer { *; }
-keepclassmembers class com.wenshu.writing.** {
    *** Companion;
}
