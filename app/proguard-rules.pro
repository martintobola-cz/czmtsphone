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

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
