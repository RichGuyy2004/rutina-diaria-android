package com.rutinadiaria.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    static final String PREFS = "routine_prefs";
    static final String KEY_HABITS = "habits_json";
    static final String KEY_PERFECT = "perfect_days";
    static final String CHANNEL_ID = "routine_reminders";
    static final int PURPLE = Color.rgb(103, 80, 164);
    static final int BG = Color.rgb(248, 247, 252);
    static final int TEXT_MUTED = Color.rgb(100, 96, 110);

    private final List<Habit> habits = new ArrayList<>();
    private LinearLayout listContainer;
    private TextView progressText;
    private TextView streakText;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        ensureChannel(this);
        habits.addAll(loadHabits(this));
        for (Habit h : habits) schedule(this, h);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 77);
        }
        buildUi();
        render();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.setBackgroundColor(BG);

        TextView title = text("Rutina diaria", 28, Color.rgb(35, 32, 40), true);
        root.addView(title);
        TextView date = text(prettyDate(LocalDate.now()), 14, TEXT_MUTED, false);
        root.addView(date);
        root.addView(space(12));

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setPadding(dp(18), dp(16), dp(18), dp(16));
        summary.setBackground(roundRect(Color.rgb(234, 221, 255), 20));

        LinearLayout summaryRow = new LinearLayout(this);
        summaryRow.setGravity(Gravity.CENTER_VERTICAL);
        progressText = text("", 16, Color.rgb(40, 35, 48), true);
        streakText = text("", 15, Color.rgb(40, 35, 48), true);
        summaryRow.addView(progressText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        summaryRow.addView(streakText);
        summary.addView(summaryRow);
        summary.addView(space(10));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        summary.addView(progressBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)));
        root.addView(summary);
        root.addView(space(16));

        root.addView(text("Línea de tiempo", 21, Color.rgb(35, 32, 40), true));
        root.addView(space(6));

        ScrollView scroll = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        Button add = new Button(this);
        add.setText("＋ Añadir meta");
        add.setTextSize(16);
        add.setTextColor(Color.WHITE);
        add.setAllCaps(false);
        add.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add.setBackground(roundRect(PURPLE, 18));
        add.setOnClickListener(v -> showHabitDialog(null));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        addLp.topMargin = dp(10);
        root.addView(add, addLp);

        setContentView(root);
    }

    private void render() {
        Collections.sort(habits, Comparator.comparingInt((Habit h) -> h.hour).thenComparingInt(h -> h.minute));
        listContainer.removeAllViews();
        LocalDate today = LocalDate.now();
        int done = 0;
        for (Habit h : habits) if (isDone(this, h.id, today)) done++;
        progressText.setText(done + " de " + habits.size() + " metas");
        streakText.setText("🔥 " + overallStreak(this) + " días");
        progressBar.setProgress(habits.isEmpty() ? 0 : (int) (100f * done / habits.size()));

        for (Habit h : habits) {
            listContainer.addView(makeHabitRow(h));
            listContainer.addView(space(8));
        }
    }

    private View makeHabitRow(Habit h) {
        boolean done = isDone(this, h.id, LocalDate.now());
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        TextView time = text(formatTime(h.hour, h.minute), 13, Color.rgb(55, 52, 60), true);
        time.setGravity(Gravity.CENTER_HORIZONTAL);
        row.addView(time, new LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(10), dp(8), dp(10));
        card.setBackground(roundRect(done ? Color.rgb(232, 222, 248) : Color.WHITE, 18));

        CheckBox check = new CheckBox(this);
        check.setChecked(done);
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        check.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setDone(this, h.id, isChecked);
            recalcPerfectToday(this, habits);
            render();
        });
        card.addView(check);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(h.title, 16, Color.rgb(35, 32, 40), true);
        center.addView(name);
        String status;
        if (done) {
            String at = completedAt(this, h.id, LocalDate.now());
            status = "Hecho" + (at == null ? "" : " a las " + at);
        } else if (h.reminder) {
            status = "Pendiente • recordatorio " + formatTime(h.hour, h.minute);
        } else {
            status = "Pendiente • sin recordatorio";
        }
        center.addView(text(status, 12, TEXT_MUTED, false));
        card.addView(center, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView fire = text("🔥 " + habitStreak(this, h.id), 13, Color.rgb(70, 63, 80), true);
        fire.setPadding(dp(6), 0, dp(8), 0);
        card.addView(fire);

        Button edit = new Button(this);
        edit.setText("Editar");
        edit.setAllCaps(false);
        edit.setTextSize(12);
        edit.setOnClickListener(v -> showHabitDialog(h));
        card.addView(edit, new LinearLayout.LayoutParams(dp(74), dp(44)));

        row.addView(card, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private void showHabitDialog(Habit existing) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), 0, dp(8), 0);

        EditText input = new EditText(this);
        input.setHint("Nombre de la meta");
        input.setSingleLine(true);
        if (existing != null) input.setText(existing.title);
        box.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));

        final int[] hour = {existing == null ? 7 : existing.hour};
        final int[] minute = {existing == null ? 0 : existing.minute};
        Button time = new Button(this);
        time.setAllCaps(false);
        time.setText("Hora: " + formatTime(hour[0], minute[0]));
        time.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            hour[0] = h;
            minute[0] = m;
            time.setText("Hora: " + formatTime(h, m));
        }, hour[0], minute[0], false).show());
        box.addView(time);

        Switch reminder = new Switch(this);
        reminder.setText("Recordatorio diario");
        reminder.setChecked(existing == null || existing.reminder);
        reminder.setPadding(0, dp(8), 0, dp(8));
        box.addView(reminder);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Nueva meta" : "Editar meta")
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null)
                .create();

        if (existing != null) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Eliminar", (d, which) -> {});
            dialog.setOnShowListener(d -> {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle("Eliminar meta")
                        .setMessage("¿Quieres eliminar “" + existing.title + "”?")
                        .setNegativeButton("No", null)
                        .setPositiveButton("Sí, eliminar", (x, y) -> {
                            cancel(this, existing.id);
                            habits.remove(existing);
                            deleteHabitData(this, existing.id);
                            saveHabits(this, habits);
                            recalcPerfectToday(this, habits);
                            dialog.dismiss();
                            render();
                        }).show());
                configureSaveButton(dialog, input, reminder, hour, minute, existing);
            });
        } else {
            dialog.setOnShowListener(d -> configureSaveButton(dialog, input, reminder, hour, minute, null));
        }
        dialog.show();
    }

    private void configureSaveButton(AlertDialog dialog, EditText input, Switch reminder, int[] hour, int[] minute, Habit existing) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError("Escribe un nombre");
                return;
            }
            Habit updated = new Habit(existing == null ? System.currentTimeMillis() : existing.id, name, hour[0], minute[0], reminder.isChecked());
            if (existing == null) habits.add(updated);
            else {
                int i = habits.indexOf(existing);
                if (i >= 0) habits.set(i, updated);
            }
            saveHabits(this, habits);
            schedule(this, updated);
            recalcPerfectToday(this, habits);
            dialog.dismiss();
            render();
        });
    }

    static class Habit {
        long id;
        String title;
        int hour;
        int minute;
        boolean reminder;
        Habit(long id, String title, int hour, int minute, boolean reminder) {
            this.id = id; this.title = title; this.hour = hour; this.minute = minute; this.reminder = reminder;
        }
    }

    static List<Habit> loadHabits(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, MODE_PRIVATE);
        String raw = p.getString(KEY_HABITS, null);
        if (raw == null || raw.isEmpty()) {
            List<Habit> defaults = new ArrayList<>();
            defaults.add(new Habit(1001, "Despertar", 7, 0, true));
            defaults.add(new Habit(1002, "Acomodar cama", 7, 5, true));
            defaults.add(new Habit(1003, "Baño", 7, 15, true));
            defaults.add(new Habit(1004, "Desayuno", 7, 30, true));
            defaults.add(new Habit(1005, "Trabajo en casa", 8, 0, true));
            defaults.add(new Habit(1006, "Comida y descanso", 12, 0, true));
            defaults.add(new Habit(1007, "Trabajo", 14, 0, true));
            defaults.add(new Habit(1008, "Tiempo libre", 18, 0, true));
            defaults.add(new Habit(1009, "Dormir", 23, 0, true));
            saveHabits(c, defaults);
            return defaults;
        }
        List<Habit> result = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                result.add(new Habit(o.getLong("id"), o.getString("title"), o.getInt("hour"), o.getInt("minute"), o.optBoolean("reminder", true)));
            }
        } catch (Exception ignored) {}
        return result;
    }

    static void saveHabits(Context c, List<Habit> list) {
        JSONArray a = new JSONArray();
        try {
            for (Habit h : list) {
                JSONObject o = new JSONObject();
                o.put("id", h.id); o.put("title", h.title); o.put("hour", h.hour); o.put("minute", h.minute); o.put("reminder", h.reminder);
                a.put(o);
            }
        } catch (Exception ignored) {}
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_HABITS, a.toString()).apply();
    }

    static boolean isDone(Context c, long id, LocalDate date) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet("done_" + id, Collections.emptySet()).contains(date.toString());
    }

    static void setDone(Context c, long id, boolean done) {
        SharedPreferences p = c.getSharedPreferences(PREFS, MODE_PRIVATE);
        String date = LocalDate.now().toString();
        Set<String> set = new HashSet<>(p.getStringSet("done_" + id, Collections.emptySet()));
        SharedPreferences.Editor e = p.edit();
        if (done) {
            set.add(date);
            e.putString("done_time_" + id + "_" + date, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        } else {
            set.remove(date);
            e.remove("done_time_" + id + "_" + date);
        }
        e.putStringSet("done_" + id, set).apply();
    }

    static String completedAt(Context c, long id, LocalDate date) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getString("done_time_" + id + "_" + date, null);
    }

    static int habitStreak(Context c, long id) {
        LocalDate d = LocalDate.now();
        if (!isDone(c, id, d)) d = d.minusDays(1);
        int n = 0;
        while (isDone(c, id, d)) { n++; d = d.minusDays(1); }
        return n;
    }

    static void recalcPerfectToday(Context c, List<Habit> list) {
        SharedPreferences p = c.getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> set = new HashSet<>(p.getStringSet(KEY_PERFECT, Collections.emptySet()));
        LocalDate today = LocalDate.now();
        boolean all = !list.isEmpty();
        for (Habit h : list) if (!isDone(c, h.id, today)) { all = false; break; }
        if (all) set.add(today.toString()); else set.remove(today.toString());
        p.edit().putStringSet(KEY_PERFECT, set).apply();
    }

    static int overallStreak(Context c) {
        Set<String> set = c.getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet(KEY_PERFECT, Collections.emptySet());
        LocalDate d = LocalDate.now();
        if (!set.contains(d.toString())) d = d.minusDays(1);
        int n = 0;
        while (set.contains(d.toString())) { n++; d = d.minusDays(1); }
        return n;
    }

    static void deleteHabitData(Context c, long id) {
        SharedPreferences p = c.getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> dates = new HashSet<>(p.getStringSet("done_" + id, Collections.emptySet()));
        SharedPreferences.Editor e = p.edit().remove("done_" + id);
        for (String date : dates) e.remove("done_time_" + id + "_" + date);
        e.apply();
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Recordatorios de rutina", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Recordatorios de tus metas y hábitos diarios");
            c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    static void schedule(Context c, Habit h) {
        cancel(c, h.id);
        if (!h.reminder) return;
        Calendar now = Calendar.getInstance();
        Calendar when = Calendar.getInstance();
        when.set(Calendar.HOUR_OF_DAY, h.hour); when.set(Calendar.MINUTE, h.minute); when.set(Calendar.SECOND, 0); when.set(Calendar.MILLISECOND, 0);
        if (!when.after(now)) when.add(Calendar.DAY_OF_YEAR, 1);
        AlarmManager am = (AlarmManager) c.getSystemService(ALARM_SERVICE);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when.getTimeInMillis(), reminderPendingIntent(c, h));
    }

    static void cancel(Context c, long id) {
        Intent i = new Intent(c, ReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, requestCode(id), i, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            ((AlarmManager)c.getSystemService(ALARM_SERVICE)).cancel(pi);
            pi.cancel();
        }
    }

    static PendingIntent reminderPendingIntent(Context c, Habit h) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.putExtra("habit_id", h.id); i.putExtra("habit_title", h.title);
        return PendingIntent.getBroadcast(c, requestCode(h.id), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static int requestCode(long id) { return (int)((id ^ (id >>> 32)) & 0x7fffffff); }

    public static class ReminderReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent intent) {
            ensureChannel(c);
            long id = intent.getLongExtra("habit_id", -1);
            String title = intent.getStringExtra("habit_title");
            if (id < 0) return;
            if (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                Intent open = new Intent(c, MainActivity.class);
                PendingIntent content = PendingIntent.getActivity(c, requestCode(id), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Notification n = new Notification.Builder(c, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle("Es hora de: " + (title == null ? "tu meta" : title))
                        .setContentText("Márcala cuando la completes y mantén tu racha.")
                        .setAutoCancel(true)
                        .setContentIntent(content)
                        .build();
                c.getSystemService(NotificationManager.class).notify(requestCode(id), n);
            }
            for (Habit h : loadHabits(c)) if (h.id == id) { schedule(c, h); break; }
        }
    }

    public static class BootReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent intent) {
            for (Habit h : loadHabits(c)) schedule(c, h);
        }
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private View space(int dp) { Space s = new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp))); return s; }
    private GradientDrawable roundRect(int color, int radiusDp) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radiusDp)); return g; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    static String formatTime(int hour, int minute) {
        String ampm = hour < 12 ? "am" : "pm";
        int h = hour % 12; if (h == 0) h = 12;
        return String.format(new Locale("es", "MX"), "%d:%02d %s", h, minute, ampm);
    }

    static String prettyDate(LocalDate date) {
        Locale locale = new Locale("es", "MX");
        String day = date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, locale);
        String month = date.getMonth().getDisplayName(java.time.format.TextStyle.FULL, locale);
        day = day.substring(0, 1).toUpperCase(locale) + day.substring(1);
        return day + ", " + date.getDayOfMonth() + " de " + month;
    }
}
