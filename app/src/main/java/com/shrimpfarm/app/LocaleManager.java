package com.shrimpfarm.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;

import java.util.Locale;

// 应用内手动切语言。语言偏好存这个 prefs，attachBaseContext 时套到 Configuration 上，
// 优先级高于系统语言：用户选过就按用户选的，没选过就跟随系统。
public final class LocaleManager {

    private static final String PREFS_NAME = "MixCalcPrefs";
    private static final String KEY_LANGUAGE = "app_language";

    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";
    // 未设置：跟随系统
    public static final String LANG_SYSTEM = "";

    private LocaleManager() {
    }

    // 返回 "zh" / "en" / ""（跟随系统）
    public static String getSavedLanguage(Context context) {
        if (context == null) {
            return LANG_SYSTEM;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String lang = prefs.getString(KEY_LANGUAGE, LANG_SYSTEM);
        return lang == null ? LANG_SYSTEM : lang;
    }

    public static void setLanguage(Context context, String lang) {
        if (context == null) {
            return;
        }
        SharedPreferences.Editor editor =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_LANGUAGE, lang == null ? LANG_SYSTEM : lang);
        editor.apply();
    }

    // 当前实际生效的语言（用户选过就用选的，否则用系统语言）
    public static String getEffectiveLanguage(Context context) {
        String saved = getSavedLanguage(context);
        if (LANG_EN.equals(saved)) {
            return LANG_EN;
        }
        if (LANG_ZH.equals(saved)) {
            return LANG_ZH;
        }
        // 跟随系统：中文环境算 zh，其余算 en
        String sys = getSystemLocale().getLanguage();
        return LANG_ZH.equals(sys) ? LANG_ZH : LANG_EN;
    }

    // 给 Context 套上选中语言，返回套好的 Context（attachBaseContext 用）
    public static Context applyLocale(Context context) {
        String saved = getSavedLanguage(context);
        LocaleList locales;
        if (LANG_EN.equals(saved)) {
            locales = new LocaleList(Locale.ENGLISH);
        } else if (LANG_ZH.equals(saved)) {
            locales = new LocaleList(Locale.SIMPLIFIED_CHINESE);
        } else {
            // 跟随系统：重新取系统 locale，不能沿用上一次手动选择留下的全局状态
            locales = getSystemLocales();
        }
        return wrap(context, locales);
    }

    private static LocaleList getSystemLocales() {
        LocaleList locales = Resources.getSystem().getConfiguration().getLocales();
        if (locales != null && !locales.isEmpty()) {
            return locales;
        }
        return new LocaleList(Locale.getDefault());
    }

    private static Locale getSystemLocale() {
        return getSystemLocales().get(0);
    }

    private static Context wrap(Context context, LocaleList locales) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        Locale.setDefault(locales.get(0));
        config.setLocales(locales);
        Resources res = context.getResources();
        res.updateConfiguration(config, res.getDisplayMetrics());
        return context.createConfigurationContext(config);
    }
}
