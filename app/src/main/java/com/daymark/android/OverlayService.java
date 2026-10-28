package com.daymark.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public class OverlayService extends Service {
    static final String ACTION_TOGGLE_CLICK_THROUGH = "com.daymark.android.TOGGLE_CLICK_THROUGH";
    static final String ACTION_DISABLE_CLICK_THROUGH = "com.daymark.android.DISABLE_CLICK_THROUGH";
    static final String ACTION_TOGGLE_LOCK = "com.daymark.android.TOGGLE_LOCK";
    static final String ACTION_CLOSE = "com.daymark.android.CLOSE_WIDGET";
    static final String ACTION_REFRESH = "com.daymark.android.REFRESH_WIDGET";
    private static final String CHANNEL_ID = "daymark_widget";
    private static final int NOTIFICATION_ID = 8701;
    private static final int SAFE_CLICKTHROUGH_TIMEOUT_MS = 30 * 60 * 1000;

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private View widgetRoot;
    private boolean clickThrough = false;
    private boolean locked = false;
    private int widthPx;
    private int heightPx;
    private int xPx;
    private int yPx;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocalDate lastRenderedDate = LocalDate.now();
    private final Runnable clickThroughFailsafe = () -> setClickThrough(false);
    private final Runnable dayWatcher = new Runnable() {
        @Override public void run() {
            LocalDate today = LocalDate.now();
            if (!today.equals(lastRenderedDate)) {
                lastRenderedDate = today;
                if (widgetRoot != null) buildWidget();
                updateNotification();
            }
            handler.postDelayed(this, 60_000L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        locked = DaymarkPrefs.get(this).getBoolean("widget_locked", false);
        clickThrough = false; // Never restore a mouse/touch-pass-through state silently.
        widthPx = dp(DaymarkPrefs.widthDp(this));
        heightPx = dp(DaymarkPrefs.heightDp(this));
        xPx = DaymarkPrefs.get(this).getInt("widget_x", dp(16));
        yPx = DaymarkPrefs.get(this).getInt("widget_y", dp(125));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        try {
            buildWidget();
            DaymarkPrefs.get(this).edit().putBoolean("overlay_running", true).apply();
            handler.postDelayed(dayWatcher, 60_000L);
        } catch (Exception e) {
            Toast.makeText(this, "Daymark couldn't open its floating widget. Check overlay permission.", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_TOGGLE_CLICK_THROUGH:
                    setClickThrough(!clickThrough);
                    break;
                case ACTION_DISABLE_CLICK_THROUGH:
                    setClickThrough(false);
                    break;
                case ACTION_TOGGLE_LOCK:
                    locked = !locked;
                    DaymarkPrefs.get(this).edit().putBoolean("widget_locked", locked).apply();
                    buildWidget();
                    break;
                case ACTION_CLOSE:
                    stopSelf();
                    break;
                case ACTION_REFRESH:
                    buildWidget();
                    updateNotification();
                    break;
                default:
                    break;
            }
        }
        return START_STICKY;
    }

    private void buildWidget() {
        if (windowManager == null) return;
        if (widgetRoot != null) {
            try { windowManager.removeView(widgetRoot); } catch (Exception ignored) {}
            widgetRoot = null;
        }
        int wdp = Math.max(220, pxToDp(widthPx));
        int hdp = Math.max(180, pxToDp(heightPx));
        int buttonDp = Math.max(22, Math.min(30, wdp / 11));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(5));
        root.setClipToPadding(false);
        root.setBackground(widgetBackground());
        root.setElevation(dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        View dragSpace = new View(this);
        header.addView(dragSpace, new LinearLayout.LayoutParams(0, dp(buttonDp), 1));
        IconButton pin = new IconButton(this, "pin", locked, buttonDp);
        pin.setContentDescription(locked ? "Unlock widget position and size" : "Lock widget position and size");
        pin.setTooltipText(pin.getContentDescription());
        pin.setOnClickListener(v -> sendAction(ACTION_TOGGLE_LOCK));
        header.addView(pin, iconParams(buttonDp));

        IconButton pointer = new IconButton(this, "pointer", clickThrough, buttonDp);
        pointer.setContentDescription(clickThrough ? "Click-through enabled; recover from notification" : "Enable click-through");
        pointer.setTooltipText(pointer.getContentDescription());
        pointer.setOnClickListener(v -> sendAction(ACTION_TOGGLE_CLICK_THROUGH));
        header.addView(pointer, iconParams(buttonDp));

        IconButton settings = new IconButton(this, "settings", false, buttonDp);
        settings.setContentDescription("Open Daymark settings");
        settings.setTooltipText("Settings");
        settings.setOnClickListener(v -> {
            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(open);
        });
        header.addView(settings, iconParams(buttonDp));

        IconButton close = new IconButton(this, "close", false, buttonDp);
        close.setContentDescription("Close floating widget");
        close.setTooltipText("Close");
        close.setOnClickListener(v -> stopSelf());
        header.addView(close, iconParams(buttonDp));
        header.setOnTouchListener(this::handleMoveGesture);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(buttonDp + 2)));

        boolean wideLayout = wdp >= 410;
        boolean showDots = (wdp >= 245 && hdp >= 250) || (wideLayout && hdp >= 175);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(wideLayout ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, 0, 1);

        LinearLayout countdown = new LinearLayout(this);
        countdown.setOrientation(LinearLayout.VERTICAL);
        countdown.setGravity(Gravity.CENTER);
        countdown.setPadding(dp(4), dp(2), dp(4), dp(2));

        TextView eventName = new TextView(this);
        eventName.setText(DaymarkPrefs.title(this));
        eventName.setTextColor(DaymarkPrefs.color(this, "primary", DaymarkPrefs.DEFAULT_PRIMARY));
        eventName.setTypeface(Typeface.create("sans-serif", Typeface.MEDIUM));
        eventName.setTextSize(Math.max(11, Math.min(17, wdp * 0.043f)));
        eventName.setGravity(Gravity.CENTER);
        eventName.setMaxLines(2);
        eventName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        countdown.addView(eventName, new LinearLayout.LayoutParams(-1, -2));

        long days = ChronoUnit.DAYS.between(LocalDate.now(), DaymarkPrefs.parsedTargetDate(this));
        String count;
        String suffix;
        if (days == 0) { count = "Today"; suffix = ""; }
        else if (days > 0) { count = String.valueOf(days); suffix = days == 1 ? "day left" : "days left"; }
        else { count = String.valueOf(Math.abs(days)); suffix = days == -1 ? "day ago" : "days ago"; }
        TextView number = new TextView(this);
        number.setText(count);
        number.setTextColor(DaymarkPrefs.color(this, "primary", DaymarkPrefs.DEFAULT_PRIMARY));
        number.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        number.setGravity(Gravity.CENTER);
        float countSize = Math.max(28, Math.min(wideLayout ? 56 : 68, Math.min(wdp * 0.22f, hdp * 0.235f)));
        number.setTextSize(countSize);
        number.setSingleLine(true);
        number.setIncludeFontPadding(false);
        countdown.addView(number, new LinearLayout.LayoutParams(-1, -2));

        TextView suffixView = new TextView(this);
        suffixView.setText(suffix);
        suffixView.setTextColor(DaymarkPrefs.color(this, "secondary", DaymarkPrefs.DEFAULT_SECONDARY));
        suffixView.setTextSize(Math.max(10, Math.min(15, wdp * 0.04f)));
        suffixView.setGravity(Gravity.CENTER);
        countdown.addView(suffixView, new LinearLayout.LayoutParams(-1, -2));

        TextView date = new TextView(this);
        date.setText(DaymarkPrefs.parsedTargetDate(this).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)).toUpperCase(Locale.ENGLISH));
        date.setTextColor(DaymarkPrefs.color(this, "secondary", DaymarkPrefs.DEFAULT_SECONDARY));
        date.setTextSize(Math.max(9, Math.min(12, wdp * 0.034f)));
        date.setGravity(Gravity.CENTER);
        date.setPadding(0, dp(5), 0, dp(1));
        countdown.addView(date, new LinearLayout.LayoutParams(-1, -2));

        if (wideLayout) {
            body.addView(countdown, new LinearLayout.LayoutParams(0, -1, 0.46f));
            if (showDots) body.addView(createDotsSection(Math.max(50, hdp - 74), true), new LinearLayout.LayoutParams(0, -1, 0.54f));
        } else {
            body.addView(countdown, new LinearLayout.LayoutParams(-1, -2));
            if (showDots) {
                int dotsHeight = Math.max(48, Math.min(104, hdp - 174));
                body.addView(createDotsSection(dotsHeight, false), new LinearLayout.LayoutParams(-1, 0, 1));
            }
        }
        root.addView(body, bodyParams);

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        ResizeHandle resize = new ResizeHandle(this, buttonDp);
        resize.setContentDescription(locked ? "Widget size locked" : "Drag to resize widget");
        resize.setOnTouchListener(this::handleResizeGesture);
        if (!locked) footer.addView(resize, new LinearLayout.LayoutParams(dp(buttonDp), dp(20)));
        else {
            TextView lockLabel = new TextView(this);
            lockLabel.setText("LOCKED");
            lockLabel.setTextColor(DaymarkPrefs.color(this, "secondary", DaymarkPrefs.DEFAULT_SECONDARY));
            lockLabel.setTextSize(8);
            footer.addView(lockLabel);
        }
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(20)));

        widgetRoot = root;
        if (windowParams == null) {
            windowParams = new WindowManager.LayoutParams(
                    widthPx,
                    heightPx,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    baseFlags(),
                    PixelFormat.TRANSLUCENT);
            windowParams.gravity = Gravity.TOP | Gravity.START;
            windowParams.x = xPx;
            windowParams.y = yPx;
        } else {
            windowParams.width = widthPx;
            windowParams.height = heightPx;
            windowParams.flags = baseFlags();
            windowParams.x = xPx;
            windowParams.y = yPx;
        }
        try {
            windowManager.addView(widgetRoot, windowParams);
        } catch (Exception e) {
            widgetRoot = null;
            stopSelf();
            return;
        }
        if (clickThrough) applyWindowFlags();
    }

    private View createDotsSection(int preferredHeightDp, boolean sideMode) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setGravity(Gravity.CENTER);
        section.setPadding(dp(sideMode ? 5 : 2), dp(2), dp(2), dp(2));
        TextView label = new TextView(this);
        label.setText("YEAR PROGRESS");
        label.setTextColor(DaymarkPrefs.color(this, "secondary", DaymarkPrefs.DEFAULT_SECONDARY));
        label.setTextSize(8);
        label.setLetterSpacing(0.08f);
        label.setGravity(Gravity.CENTER);
        section.addView(label, new LinearLayout.LayoutParams(-1, dp(14)));
        YearDotsView dots = new YearDotsView(this);
        section.addView(dots, new LinearLayout.LayoutParams(-1, 0, 1));
        return section;
    }

    private boolean handleMoveGesture(View v, MotionEvent event) {
        if (locked || clickThrough || windowParams == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                v.setTag(new float[]{event.getRawX(), event.getRawY(), windowParams.x, windowParams.y});
                return true;
            case MotionEvent.ACTION_MOVE:
                Object tag = v.getTag();
                if (!(tag instanceof float[])) return true;
                float[] data = (float[]) tag;
                int maxX = Math.max(0, getResources().getDisplayMetrics().widthPixels - widthPx);
                int maxY = Math.max(0, getResources().getDisplayMetrics().heightPixels - heightPx);
                xPx = clamp((int) (data[2] + event.getRawX() - data[0]), 0, maxX);
                yPx = clamp((int) (data[3] + event.getRawY() - data[1]), 0, maxY);
                windowParams.x = xPx;
                windowParams.y = yPx;
                try { windowManager.updateViewLayout(widgetRoot, windowParams); } catch (Exception ignored) {}
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                savePosition();
                v.setTag(null);
                return true;
            default:
                return true;
        }
    }

    private boolean handleResizeGesture(View v, MotionEvent event) {
        if (locked || clickThrough || windowParams == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                v.setTag(new float[]{event.getRawX(), event.getRawY(), widthPx, heightPx});
                return true;
            case MotionEvent.ACTION_MOVE:
                Object tag = v.getTag();
                if (!(tag instanceof float[])) return true;
                float[] data = (float[]) tag;
                int minW = dp(220), minH = dp(180);
                int maxW = Math.max(minW, getResources().getDisplayMetrics().widthPixels - dp(12));
                int maxH = Math.max(minH, getResources().getDisplayMetrics().heightPixels - dp(100));
                widthPx = clamp((int) (data[2] + event.getRawX() - data[0]), minW, maxW);
                heightPx = clamp((int) (data[3] + event.getRawY() - data[1]), minH, maxH);
                windowParams.width = widthPx;
                windowParams.height = heightPx;
                try { windowManager.updateViewLayout(widgetRoot, windowParams); } catch (Exception ignored) {}
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                saveSizeAndPosition();
                v.setTag(null);
                buildWidget();
                return true;
            default:
                return true;
        }
    }

    private void setClickThrough(boolean enabled) {
        if (enabled && !notificationsEnabled()) {
            Toast.makeText(this, "Enable Daymark notifications first so you can safely turn click-through off.", Toast.LENGTH_LONG).show();
            try {
                Intent settings = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                settings.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(settings);
            } catch (Exception ignored) {
                Intent app = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(app);
            }
            return;
        }
        clickThrough = enabled;
        if (clickThrough) {
            handler.removeCallbacks(clickThroughFailsafe);
            handler.postDelayed(clickThroughFailsafe, SAFE_CLICKTHROUGH_TIMEOUT_MS);
        } else {
            handler.removeCallbacks(clickThroughFailsafe);
        }
        applyWindowFlags();
        updateNotification();
        if (widgetRoot != null) buildWidget();
        Toast.makeText(this, clickThrough ? "Click-through on. Turn it off from the Daymark notification." : "Widget interaction restored.", Toast.LENGTH_SHORT).show();
    }

    private void applyWindowFlags() {
        if (windowParams == null || widgetRoot == null) return;
        windowParams.flags = baseFlags();
        if (clickThrough) windowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try { windowManager.updateViewLayout(widgetRoot, windowParams); } catch (Exception ignored) {}
    }

    private int baseFlags() {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    }

    private boolean notificationsEnabled() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return nm == null || nm.areNotificationsEnabled();
    }

    private void sendAction(String action) {
        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Daymark floating widget", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Controls for the floating countdown widget and click-through recovery.");
            channel.setShowBadge(false);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 100, open, pendingFlags());
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Daymark floating widget")
                .setContentText(clickThrough
                        ? "Click-through is ON · Use the action below to restore touch."
                        : "Your countdown is floating above other apps.")
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setPriority(Notification.PRIORITY_LOW);
        if (clickThrough) {
            Intent disable = new Intent(this, OverlayService.class).setAction(ACTION_DISABLE_CLICK_THROUGH);
            PendingIntent disablePending = PendingIntent.getService(this, 101, disable, pendingFlags());
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Turn click-through OFF", disablePending);
        } else {
            builder.addAction(android.R.drawable.ic_menu_manage, "Open settings", openPending);
        }
        return builder.build();
    }

    private int pendingFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
    }

    private GradientDrawable widgetBackground() {
        int color = DaymarkPrefs.color(this, "background", DaymarkPrefs.DEFAULT_BACKGROUND);
        int alpha = Math.round(DaymarkPrefs.opacity(this) * 2.55f);
        int fill = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(17));
        bg.setStroke(dp(1), DaymarkPrefs.color(this, "border", DaymarkPrefs.DEFAULT_BORDER));
        return bg;
    }

    private void savePosition() {
        DaymarkPrefs.get(this).edit().putInt("widget_x", xPx).putInt("widget_y", yPx).apply();
    }

    private void saveSizeAndPosition() {
        savePosition();
        DaymarkPrefs.get(this).edit()
                .putInt("widget_width_dp", pxToDp(widthPx))
                .putInt("widget_height_dp", pxToDp(heightPx)).apply();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int pxToDp(int value) {
        return (int) (value / getResources().getDisplayMetrics().density + 0.5f);
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private LinearLayout.LayoutParams iconParams(int dp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(dp), dp(dp));
        p.leftMargin = dp(2);
        return p;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(clickThroughFailsafe);
        handler.removeCallbacks(dayWatcher);
        if (widgetRoot != null && windowManager != null) {
            try { windowManager.removeView(widgetRoot); } catch (Exception ignored) {}
            widgetRoot = null;
        }
        DaymarkPrefs.get(this).edit().putBoolean("overlay_running", false).apply();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private final class IconButton extends View {
        private final String kind;
        private final boolean active;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int sizeDp;
        IconButton(Context context, String kind, boolean active, int sizeDp) {
            super(context);
            this.kind = kind;
            this.active = active;
            this.sizeDp = sizeDp;
            setBackground(makeButtonBg(active));
            setClickable(true);
            setFocusable(true);
        }
        private GradientDrawable makeButtonBg(boolean selected) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(selected ? Color.argb(38, 217, 248, 106) : Color.TRANSPARENT);
            bg.setCornerRadius(dp(8));
            return bg;
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float s = Math.min(getWidth(), getHeight());
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float u = s * 0.23f;
            paint.setColor(active ? DaymarkPrefs.color(OverlayService.this, "accent", DaymarkPrefs.DEFAULT_ACCENT)
                    : DaymarkPrefs.color(OverlayService.this, "primary", DaymarkPrefs.DEFAULT_PRIMARY));
            paint.setStrokeWidth(Math.max(dp(1.4f), s * 0.055f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            switch (kind) {
                case "pin": {
                    canvas.drawLine(cx, cy - u * 1.25f, cx, cy + u * 0.65f, paint);
                    canvas.drawLine(cx - u, cy - u * 1.0f, cx + u, cy - u * 1.0f, paint);
                    canvas.drawLine(cx - u * 0.75f, cy - u, cx - u * 0.75f, cy - u * 0.15f, paint);
                    canvas.drawLine(cx + u * 0.75f, cy - u, cx + u * 0.75f, cy - u * 0.15f, paint);
                    canvas.drawLine(cx - u * 0.75f, cy - u * 0.15f, cx + u * 0.75f, cy - u * 0.15f, paint);
                    canvas.drawLine(cx, cy + u * 0.5f, cx - u * 0.7f, cy + u * 1.25f, paint);
                    break;
                }
                case "pointer": {
                    android.graphics.Path path = new android.graphics.Path();
                    path.moveTo(cx - u * 0.9f, cy - u * 1.35f);
                    path.lineTo(cx + u * 0.95f, cy + u * 0.15f);
                    path.lineTo(cx + u * 0.15f, cy + u * 0.25f);
                    path.lineTo(cx + u * 0.65f, cy + u * 1.2f);
                    path.lineTo(cx + u * 0.15f, cy + u * 1.45f);
                    path.lineTo(cx - u * 0.3f, cy + u * 0.45f);
                    path.lineTo(cx - u * 0.9f, cy + u * 0.8f);
                    path.close();
                    canvas.drawPath(path, paint);
                    if (active) {
                        paint.setStyle(Paint.Style.FILL);
                        canvas.drawCircle(cx, cy, s * 0.035f, paint);
                    }
                    break;
                }
                case "settings": {
                    canvas.drawLine(cx - u, cy - u * 0.8f, cx + u, cy - u * 0.8f, paint);
                    canvas.drawLine(cx - u, cy, cx + u, cy, paint);
                    canvas.drawLine(cx - u, cy + u * 0.8f, cx + u, cy + u * 0.8f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(cx - u * 0.3f, cy - u * 0.8f, Math.max(dp(2), s * 0.07f), paint);
                    canvas.drawCircle(cx + u * 0.45f, cy, Math.max(dp(2), s * 0.07f), paint);
                    canvas.drawCircle(cx - u * 0.1f, cy + u * 0.8f, Math.max(dp(2), s * 0.07f), paint);
                    break;
                }
                case "close":
                    canvas.drawLine(cx - u * 0.75f, cy - u * 0.75f, cx + u * 0.75f, cy + u * 0.75f, paint);
                    canvas.drawLine(cx + u * 0.75f, cy - u * 0.75f, cx - u * 0.75f, cy + u * 0.75f, paint);
                    break;
            }
        }
    }

    private final class ResizeHandle extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ResizeHandle(Context context, int sizeDp) { super(context); setMinimumWidth(dp(sizeDp)); setMinimumHeight(dp(20)); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setColor(DaymarkPrefs.color(OverlayService.this, "secondary", DaymarkPrefs.DEFAULT_SECONDARY));
            paint.setStrokeWidth(dp(1.4f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            float right = getWidth() - dp(4), bottom = getHeight() - dp(4);
            for (int i = 0; i < 3; i++) {
                float offset = dp(i * 4 + 3);
                canvas.drawLine(right - offset, bottom, right, bottom - offset, paint);
            }
        }
    }

    private final class YearDotsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int daysInYear;
        private final int todayIndex;
        YearDotsView(Context context) {
            super(context);
            LocalDate now = LocalDate.now();
            daysInYear = now.isLeapYear() ? 366 : 365;
            todayIndex = now.getDayOfYear() - 1;
            setContentDescription("Year progress dots. Passed days are muted, today is accent colored, remaining days use the main text color.");
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float density = getResources().getDisplayMetrics().density;
            int columns = Math.max(12, Math.min(46, (int) (w / (5.8f * density))));
            int rows = (int) Math.ceil(daysInYear / (float) columns);
            float cellW = w / columns;
            float cellH = h / rows;
            float radius = Math.max(density * 1.1f, Math.min(density * 2.7f, Math.min(cellW, cellH) * 0.31f));
            int past = DaymarkPrefs.color(OverlayService.this, "secondary", DaymarkPrefs.DEFAULT_SECONDARY);
            int future = DaymarkPrefs.color(OverlayService.this, "primary", DaymarkPrefs.DEFAULT_PRIMARY);
            int accent = DaymarkPrefs.color(OverlayService.this, "accent", DaymarkPrefs.DEFAULT_ACCENT);
            for (int i = 0; i < daysInYear; i++) {
                int row = i / columns;
                int col = i % columns;
                paint.setColor(i < todayIndex ? withAlpha(past, 155) : (i == todayIndex ? accent : future));
                paint.setStyle(Paint.Style.FILL);
                float cx = cellW * (col + 0.5f);
                float cy = cellH * (row + 0.5f);
                canvas.drawCircle(cx, cy, radius, paint);
            }
        }
        private int withAlpha(int color, int alpha) {
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        }
    }
}
