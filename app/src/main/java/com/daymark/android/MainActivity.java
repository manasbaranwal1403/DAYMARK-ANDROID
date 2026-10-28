package com.daymark.android;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY = 2201;
    private static final int REQUEST_NOTIFICATIONS = 2202;

    private EditText titleInput;
    private TextView dateButton;
    private TextView previewNumber;
    private TextView previewDate;
    private SeekBar opacitySlider;
    private TextView opacityValue;
    private CheckBox autoStartCheck;
    private TextView statusText;
    private final Map<String, EditText> colorInputs = new LinkedHashMap<>();
    private String chosenDate = DaymarkPrefs.DEFAULT_DATE;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        configureSystemBars();
        buildScreen();
        requestNotificationsIfNeeded();
    }

    private void configureSystemBars() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) w.setDecorFitsSystemWindows(true);
        w.setStatusBarColor(Color.rgb(16, 17, 18));
        w.setNavigationBarColor(Color.rgb(16, 17, 18));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            w.getDecorView().setSystemUiVisibility(0);
        }
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(16, 17, 18));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(22), dp(20), dp(36));
        scroll.addView(content);

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("D", 22, Color.parseColor(DaymarkPrefs.DEFAULT_ACCENT), true);
        mark.setGravity(Gravity.CENTER);
        GradientDrawable markBg = rounded(Color.parseColor(DaymarkPrefs.DEFAULT_BACKGROUND), dp(15), Color.parseColor(DaymarkPrefs.DEFAULT_BORDER), dp(1));
        mark.setBackground(markBg);
        header.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout titles = column();
        titles.setPadding(dp(14), 0, 0, 0);
        TextView heading = text("Daymark", 24, Color.WHITE, true);
        TextView subtitle = text("Your time, in focus.", 13, Color.parseColor("#969BA3"), false);
        titles.addView(heading);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(header);
        addSpace(content, 22);

        TextView intro = text("COUNTDOWN SETTINGS", 11, Color.parseColor(DaymarkPrefs.DEFAULT_ACCENT), true);
        intro.setLetterSpacing(0.12f);
        content.addView(intro);
        addSpace(content, 10);

        LinearLayout eventCard = card();
        eventCard.addView(text("Your event", 17, Color.WHITE, true));
        addSpace(eventCard, 14);
        eventCard.addView(text("EVENT NAME", 10, Color.parseColor("#969BA3"), true));
        titleInput = new EditText(this);
        titleInput.setSingleLine(true);
        titleInput.setTextSize(15);
        titleInput.setTextColor(Color.WHITE);
        titleInput.setHintTextColor(Color.parseColor("#747982"));
        titleInput.setHint("e.g. Days left in 2026");
        titleInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        titleInput.setBackground(rounded(Color.parseColor("#202226"), dp(12), Color.parseColor("#373A40"), dp(1)));
        titleInput.setText(DaymarkPrefs.title(this));
        eventCard.addView(titleInput, matchWrap());
        addSpace(eventCard, 14);
        eventCard.addView(text("TARGET DATE", 10, Color.parseColor("#969BA3"), true));
        dateButton = buttonBase(this, "");
        dateButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        dateButton.setCompoundDrawablePadding(dp(8));
        chosenDate = DaymarkPrefs.targetDate(this);
        dateButton.setText(formatDate(chosenDate));
        dateButton.setOnClickListener(v -> showDatePicker());
        eventCard.addView(dateButton, matchWrap());
        addSpace(eventCard, 18);

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(16), dp(18), dp(16), dp(18));
        preview.setBackground(rounded(Color.parseColor("#111315"), dp(14), Color.parseColor("#30343A"), dp(1)));
        preview.addView(text("PREVIEW", 9, Color.parseColor("#858A92"), true));
        addSpace(preview, 7);
        previewNumber = text("64 days left", 34, Color.WHITE, true);
        previewNumber.setGravity(Gravity.CENTER);
        preview.addView(previewNumber);
        previewDate = text("31 DEC 2026", 11, Color.parseColor("#969BA3"), true);
        previewDate.setGravity(Gravity.CENTER);
        preview.addView(previewDate);
        eventCard.addView(preview, matchWrap());
        content.addView(eventCard);
        addSpace(content, 16);

        LinearLayout appearanceCard = card();
        appearanceCard.addView(text("Appearance", 17, Color.WHITE, true));
        addSpace(appearanceCard, 4);
        appearanceCard.addView(text("Tune the floating widget. Changes are saved on your phone.", 12, Color.parseColor("#969BA3"), false));
        addSpace(appearanceCard, 20);
        LinearLayout opacityLabels = row();
        opacityLabels.setGravity(Gravity.CENTER_VERTICAL);
        opacityLabels.addView(text("BACKGROUND OPACITY", 10, Color.parseColor("#969BA3"), true), new LinearLayout.LayoutParams(0, -2, 1));
        opacityValue = text(DaymarkPrefs.opacity(this) + "%", 12, Color.parseColor(DaymarkPrefs.DEFAULT_ACCENT), true);
        opacityLabels.addView(opacityValue);
        appearanceCard.addView(opacityLabels);
        opacitySlider = new SeekBar(this);
        opacitySlider.setMax(75);
        opacitySlider.setProgress(DaymarkPrefs.opacity(this) - 25);
        opacitySlider.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(DaymarkPrefs.DEFAULT_ACCENT)));
        opacitySlider.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(DaymarkPrefs.DEFAULT_ACCENT)));
        opacitySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                opacityValue.setText((progress + 25) + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        appearanceCard.addView(opacitySlider, matchWrap());
        addSpace(appearanceCard, 18);
        appearanceCard.addView(text("COLORS · HEX", 10, Color.parseColor("#969BA3"), true));
        addSpace(appearanceCard, 8);
        addColorInput(appearanceCard, "Widget background", "background", DaymarkPrefs.DEFAULT_BACKGROUND);
        addColorInput(appearanceCard, "Main text and future dots", "primary", DaymarkPrefs.DEFAULT_PRIMARY);
        addColorInput(appearanceCard, "Supporting text and passed dots", "secondary", DaymarkPrefs.DEFAULT_SECONDARY);
        addColorInput(appearanceCard, "Accent and today", "accent", DaymarkPrefs.DEFAULT_ACCENT);
        addColorInput(appearanceCard, "Border and dividers", "border", DaymarkPrefs.DEFAULT_BORDER);
        content.addView(appearanceCard);
        addSpace(content, 16);

        LinearLayout behaviorCard = card();
        behaviorCard.addView(text("Floating widget", 17, Color.WHITE, true));
        addSpace(behaviorCard, 6);
        behaviorCard.addView(text("Android keeps the widget above other apps using its system overlay permission. You can move and resize it on screen.", 12, Color.parseColor("#969BA3"), false));
        addSpace(behaviorCard, 10);
        autoStartCheck = new CheckBox(this);
        autoStartCheck.setText("Start the widget when the phone starts");
        autoStartCheck.setTextColor(Color.WHITE);
        autoStartCheck.setTextSize(13);
        autoStartCheck.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(DaymarkPrefs.DEFAULT_ACCENT)));
        autoStartCheck.setChecked(DaymarkPrefs.autoStart(this));
        behaviorCard.addView(autoStartCheck);
        addSpace(behaviorCard, 8);
        behaviorCard.addView(text("Click-through mode passes touches to the app behind the widget. Use the persistent notification action to turn it off again.", 12, Color.parseColor("#969BA3"), false));
        content.addView(behaviorCard);
        addSpace(content, 16);

        statusText = text("", 12, Color.parseColor("#D9F86A"), false);
        statusText.setPadding(dp(4), dp(2), dp(4), dp(8));
        content.addView(statusText);

        Button save = primaryButton("Save changes");
        save.setOnClickListener(v -> saveSettings(true));
        content.addView(save, matchWrap());
        addSpace(content, 10);

        Button start = secondaryButton("Start / refresh floating widget");
        start.setOnClickListener(v -> {
            if (saveSettings(false)) startWidget();
        });
        content.addView(start, matchWrap());
        addSpace(content, 10);

        Button stop = secondaryButton("Close floating widget");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            DaymarkPrefs.get(this).edit().putBoolean("overlay_running", false).apply();
            updateStatus();
            Toast.makeText(this, "Floating widget closed", Toast.LENGTH_SHORT).show();
        });
        content.addView(stop, matchWrap());
        addSpace(content, 12);
        TextView footer = text("DAYMARK FOR ANDROID  ·  SETTINGS ARE STORED LOCALLY", 9, Color.parseColor("#666B73"), true);
        footer.setGravity(Gravity.CENTER);
        content.addView(footer);

        setContentView(scroll);
        updatePreview();
        updateStatus();
        titleInput.addTextChangedListener(new SimpleTextWatcher(this::updatePreview));
    }

    private void addColorInput(LinearLayout parent, String label, String key, String fallback) {
        parent.addView(text(label, 12, Color.parseColor("#D4D7DC"), false));
        addSpace(parent, 5);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(13);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#747982"));
        input.setHint("#RRGGBB");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackground(rounded(Color.parseColor("#202226"), dp(10), Color.parseColor("#373A40"), dp(1)));
        input.setText(DaymarkPrefs.colorText(this, key, fallback));
        colorInputs.put(key, input);
        parent.addView(input, matchWrap());
        addSpace(parent, 12);
    }

    private boolean saveSettings(boolean showToast) {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) title = "Countdown";
        Map<String, String> colors = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, EditText> entry : colorInputs.entrySet()) {
                String value = entry.getValue().getText().toString().trim();
                if (!value.startsWith("#")) value = "#" + value;
                Color.parseColor(value);
                colors.put(entry.getKey(), value.toUpperCase(Locale.ROOT));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Check the color values. Use HEX such as #D9F86A.", Toast.LENGTH_LONG).show();
            return false;
        }
        try { LocalDate.parse(chosenDate); }
        catch (Exception e) {
            Toast.makeText(this, "Choose a valid target date.", Toast.LENGTH_SHORT).show();
            return false;
        }
        SharedPreferences.Editor editor = DaymarkPrefs.get(this).edit()
                .putString("title", title)
                .putString("target_date", chosenDate)
                .putInt("opacity", opacitySlider.getProgress() + 25)
                .putBoolean("auto_start", autoStartCheck.isChecked());
        for (Map.Entry<String, String> entry : colors.entrySet()) editor.putString(entry.getKey(), entry.getValue());
        editor.apply();
        updatePreview();

        boolean running = DaymarkPrefs.isOverlayRunning(this);
        if (running && Settings.canDrawOverlays(this)) {
            Intent refresh = new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_REFRESH);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(refresh);
            else startService(refresh);
        }
        updateStatus();
        if (showToast) Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        return true;
    }

    private void startWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY);
            Toast.makeText(this, "Allow Daymark to display over other apps, then return here.", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
        startOverlayService();
    }

    private void startOverlayService() {
        try {
            Intent service = new Intent(this, OverlayService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
            else startService(service);
            Toast.makeText(this, "Floating widget started", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't start the widget. Check overlay permission in Android Settings.", Toast.LENGTH_LONG).show();
        }
        updateStatus();
    }

    private void updatePreview() {
        if (previewNumber == null || previewDate == null) return;
        try {
            LocalDate target = LocalDate.parse(chosenDate);
            long days = ChronoUnit.DAYS.between(LocalDate.now(), target);
            String value;
            if (days == 0) value = "Today";
            else if (days > 0) value = days + (days == 1 ? " day left" : " days left");
            else value = Math.abs(days) + (days == -1 ? " day ago" : " days ago");
            previewNumber.setText(value);
            previewDate.setText(target.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)).toUpperCase(Locale.ENGLISH));
        } catch (Exception ignored) {
            previewNumber.setText("— days left");
        }
    }

    private void updateStatus() {
        if (statusText == null) return;
        boolean overlayPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
        boolean running = DaymarkPrefs.isOverlayRunning(this);
        if (running) statusText.setText("WIDGET STATUS  ·  RUNNING");
        else if (!overlayPermission) statusText.setText("WIDGET STATUS  ·  ALLOW DISPLAY OVER OTHER APPS TO START");
        else statusText.setText("WIDGET STATUS  ·  NOT RUNNING");
    }

    private void showDatePicker() {
        LocalDate initial;
        try { initial = LocalDate.parse(chosenDate); }
        catch (Exception ignored) { initial = LocalDate.now(); }
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            chosenDate = LocalDate.of(year, month + 1, day).toString();
            dateButton.setText(formatDate(chosenDate));
            updatePreview();
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth());
        dialog.show();
    }

    private String formatDate(String date) {
        try { return LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy", Locale.ENGLISH)); }
        catch (Exception ignored) { return date; }
    }

    private void requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY) {
            updateStatus();
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) startOverlayService();
        }
    }

    private LinearLayout card() {
        LinearLayout v = column();
        v.setPadding(dp(16), dp(18), dp(16), dp(18));
        v.setBackground(rounded(Color.parseColor("#191B1F"), dp(18), Color.parseColor("#30343A"), dp(1)));
        return v;
    }

    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout row() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        return v;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        else v.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        v.setIncludeFontPadding(true);
        return v;
    }

    private Button buttonBase(Context context, String label) {
        Button b = new Button(context);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(12), dp(9), dp(12), dp(9));
        b.setBackground(rounded(Color.parseColor("#202226"), dp(12), Color.parseColor("#3A3E45"), dp(1)));
        return b;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.parseColor("#17191C"));
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        b.setBackground(rounded(Color.parseColor(DaymarkPrefs.DEFAULT_ACCENT), dp(14), Color.TRANSPARENT, 0));
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = buttonBase(this, label);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radius);
        if (strokeWidth > 0) bg.setStroke(strokeWidth, strokeColor);
        return bg;
    }

    private void addSpace(LinearLayout parent, int heightDp) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class SimpleTextWatcher implements android.text.TextWatcher {
        private final Runnable callback;
        SimpleTextWatcher(Runnable callback) { this.callback = callback; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { callback.run(); }
        @Override public void afterTextChanged(android.text.Editable s) {}
    }
}
