-renamesourcefileattribute SourceFile
-keepattributes SourceFile, LineNumberTable

-dontwarn com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool
-dontwarn com.bumptech.glide.load.resource.bitmap.Downsampler
-dontwarn com.bumptech.glide.load.resource.bitmap.HardwareConfigState
-dontwarn com.bumptech.glide.manager.RequestManagerRetriever

-keep public class * extends java.lang.Exception

-keep class android.support.v7.widget.SearchView { *; }
-keep class cz.mts.base.models.PhoneNumber { *; }

# Toolbar overflow "..." tlačítko + jeho interní popup
-keep class androidx.appcompat.widget.ActionMenuPresenter { *; }
-keep class androidx.appcompat.widget.ActionMenuPresenter$OverflowMenuButton { *; }
-keep class androidx.appcompat.widget.ActionMenuView { *; }
-keep class androidx.appcompat.widget.Toolbar { *; }

# PopupMenu (androidx) + jeho interní popup implementace
-keep class androidx.appcompat.widget.PopupMenu { *; }
-keep class androidx.appcompat.view.menu.MenuPopupHelper { *; }
-keep class androidx.appcompat.view.menu.StandardMenuPopup { *; }
-keep class androidx.appcompat.view.menu.CascadingMenuPopup { *; }
-keep class androidx.appcompat.view.menu.CascadingMenuPopup$CascadingMenuInfo { *; }

# skutečné popup okno, kterému nastavujeme pozadí
-keep class androidx.appcompat.widget.ListPopupWindow { *; }
-keep class androidx.appcompat.widget.MenuPopupWindow { *; }

# ať se přes ně dá dál reflexí procházet i vnořené interface implementace
-keepclassmembers class androidx.appcompat.view.menu.** { *; }
-keepclassmembers class androidx.appcompat.widget.** { *; }


# Joda
-dontwarn org.joda.convert.**
-dontwarn org.joda.time.**
-keep class org.joda.time.** { *; }
-keep interface org.joda.time.** { *; }

-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn javax.swing.tree.TreeNode

#Gson https://github.com/google/gson/blob/main/gson/src/main/resources/META-INF/proguard/gson.pro
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-if class com.google.gson.reflect.TypeToken
-keep,allowobfuscation class com.google.gson.reflect.TypeToken

-keep,allowobfuscation class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowoptimization @com.google.gson.annotations.JsonAdapter class *

-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.Expose <fields>;
  @com.google.gson.annotations.JsonAdapter <fields>;
  @com.google.gson.annotations.Since <fields>;
  @com.google.gson.annotations.Until <fields>;
}

-keepclassmembers class * extends com.google.gson.TypeAdapter {
  <init>();
}
-keepclassmembers class * implements com.google.gson.TypeAdapterFactory {
  <init>();
}
-keepclassmembers class * implements com.google.gson.JsonSerializer {
  <init>();
}
-keepclassmembers class * implements com.google.gson.JsonDeserializer {
  <init>();
}

-if class *
-keepclasseswithmembers,allowobfuscation class <1> {
  @com.google.gson.annotations.SerializedName <fields>;
}
-if class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers,allowobfuscation,allowoptimization class <1> {
  <init>();
}
