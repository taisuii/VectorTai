-keep class android.** { *; }
-keep class dev.android.runtime.ext.** { *; }

# Workaround to bypass verification of in-memory built class xposed.dummy.XResourcesSuperClass
-keepclassmembers class org.matrix.vector.legacy.LegacyDelegateImpl$ResourceProxy { *; }
