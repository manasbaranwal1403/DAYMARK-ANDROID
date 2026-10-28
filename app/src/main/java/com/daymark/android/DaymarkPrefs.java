package com.daymark.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import java.time.LocalDate;

final class DaymarkPrefs {
    static final String FILE = "daymark_settings";
    static final String DEFAULT_TITLE = "Days left in 2026";
    static final String DEFAULT_DATE = "2026-12-31";
    static final String DEFAULT_BACKGROUND = "#17191C";
    static final String DEFAULT_PRIMARY = "#F4F5F6";
    static final String DEFAULT_SECONDARY = "#969BA3";
    static final String DEFAULT_ACCENT = "#D9F86A";
    static final String DEFAULT_BORDER = "#383C42";

    private DaymarkPrefs() {}

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static String title(Context c) {
        return get(c).getString("title", DEFAULT_TITLE);
    }

    static String targetDate(Context c) {
        return get(c).getString("target_date", DEFAULT_DATE);
    }

    static LocalDate parsedTargetDate(Context c) {
        try {
            return LocalDate.parse(targetDate(c));
        } catch (Exception ignored) {
            return LocalDate.parse(DEFAULT_DATE);
        }
    }

    static int opacity(Context c) {
        return Math.max(25, Math.min(100, get(c).getInt("opacity", 94)));
    }

    static String colorText(Context c, String key, String fallback) {
        return get(c).getString(key, fallback);
    }

    static int color(Context c, String key, String fallback) {
        try {
            return Color.parseColor(colorText(c, key, fallback));
        } catch (Exception ignored) {
            return Color.parseColor(fallback);
        }
    }

    static boolean autoStart(Context c) {
        return get(c).getBoolean("auto_start", false);
    }

    static boolean isOverlayRunning(Context c) {
        return get(c).getBoolean("overlay_running", false);
    }

    static int widthDp(Context c) {
        return Math.max(220, get(c).getInt("widget_width_dp", 320));
    }

    static int heightDp(Context c) {
        return Math.max(180, get(c).getInt("widget_height_dp", 290));
    }
}
