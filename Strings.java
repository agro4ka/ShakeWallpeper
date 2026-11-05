package com.shake.wallpaper;

import java.util.Locale;

/** Простейшая локализация EN / RU. */
final class Strings {
    private static boolean ru = false;

    static void setLang(String lang) {
        ru = "ru".equals(lang);
    }

    static boolean isRu() {
        return ru;
    }

    static String defaultLang() {
        return "ru".equals(Locale.getDefault().getLanguage()) ? "ru" : "en";
    }

    static String t(String key) {
        for (String[] row : S) {
            if (row[0].equals(key)) return ru ? row[2] : row[1];
        }
        return key;
    }

    static String t(String key, Object... args) {
        return String.format(t(key), args);
    }

    private static final String[][] S = {
            {"title", "Shake Wallpaper", "Shake Wallpaper"},
            {"lang_btn", "Language: English", "Язык: Русский"},
            {"areas", "Areas", "Области"},
            {"area_n", "Area %d", "Область %d"},
            {"add_area", "+ Add area", "+ Добавить область"},
            {"del_area", "Delete area", "Удалить область"},
            {"mode_move", "Move frame", "Двигать кадр"},
            {"mode_areas", "Back to areas", "Назад к областям"},
            {"hint_edit", "Drag an area to move it, drag the dots on its edges to resize it.",
                    "Перетаскивайте область, чтобы двигать её; точки на краях меняют размер."},
            {"hint_crop", "Drag the frame to choose which part of the image is visible on the screen.",
                    "Перетаскивайте рамку, чтобы выбрать, какая часть картинки видна на экране."},
            {"no_area", "No areas yet. Tap “Add area”, then drag it over the part that should jiggle.",
                    "Областей пока нет. Нажмите «Добавить область» и перетащите её на часть, которая должна трястись."},
            {"settings_for", "Settings: area %d", "Настройки: область %d"},
            {"apply_all", "Apply these settings to all areas", "Применить эти настройки ко всем областям"},
            {"sens", "Sensitivity", "Чувствительность"},
            {"sens_d", "How easily it triggers from shaking", "Насколько легко срабатывает от тряски"},
            {"amp", "Amplitude", "Амплитуда"},
            {"amp_d", "How far it moves when shaken", "Насколько далеко смещается при тряске"},
            {"damp", "Duration", "Длительность"},
            {"damp_d", "How long it keeps jiggling (higher = longer)", "Как долго продолжает трястись (больше = дольше)"},
            {"speed", "Speed", "Скорость"},
            {"speed_d", "Oscillation speed (higher = faster)", "Скорость колебаний (больше = быстрее)"},
            {"touch", "Touch", "Касание"},
            {"touch_d", "Touch push strength", "Сила толчка от касания"},
            {"common", "Common settings", "Общие настройки"},
            {"zoom", "Zoom", "Масштаб"},
            {"zoom_d", "How zoomed in the image is", "Насколько увеличена картинка"},
            {"image", "Image", "Картинка"},
            {"builtin", "Built-in image", "Встроенная картинка"},
            {"custom", "Custom image", "Своя картинка"},
            {"reset", "Reset…", "Сброс…"},
            {"reset_q", "What to reset?", "Что сбросить?"},
            {"reset_area", "Settings of selected area", "Настройки выбранной области"},
            {"reset_areas", "Remove all areas", "Удалить все области"},
            {"reset_image", "Image and frame", "Картинку и кадр"},
            {"reset_all", "Everything", "Всё"},
            {"cancel", "Cancel", "Отмена"},
            {"set_wp", "Set as wallpaper", "Установить как обои"},
            {"max_areas", "Maximum number of areas reached", "Достигнуто максимальное число областей"},
            {"img_fail", "Could not load the image", "Не удалось загрузить картинку"},
    };
}
