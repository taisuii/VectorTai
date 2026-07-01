-keep class android.** { *; }
-keep class com.android.bridge.** { *; }

# Workaround to bypass verification of in-memory built class xposed.dummy.XResourcesSuperClass
-keepclassmembers class org.matrix.vector.legacy.LegacyDelegateImpl$ResourceProxy { *; }
