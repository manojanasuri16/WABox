# Navigation type-safe routes are @Serializable objects/classes.
-keepclassmembers @kotlinx.serialization.Serializable class com.wabox.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.wabox.app.**$$serializer { *; }
# Glance ActionCallbacks are instantiated reflectively by class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
