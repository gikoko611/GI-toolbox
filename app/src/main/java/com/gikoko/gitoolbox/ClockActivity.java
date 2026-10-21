package com.gikoko.gitoolbox;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Stopwatch / Timer / Pomodoro / World Clock / Countdown / Alarm. */
public class ClockActivity extends Activity {

    private String tool;
    private Handler handler = new Handler();
    private TextView big, sub;
    private long startAt, elapsed;
    private boolean running;
    private Runnable ticker;
    private ToneGenerator tone;

    // timer
    private long targetMs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tool = getIntent().getStringExtra("tool");
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.header(this, tool, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));

        big = Ui.text(this, "00:00.0", 36, Ui.GREEN, true);
        big.setTypeface(android.graphics.Typeface.MONOSPACE);
        big.setGravity(android.view.Gravity.CENTER);
        LinearLayout card = Ui.card(this);
        card.addView(big);
        sub = Ui.text(this, "", 12, Ui.DIM, false);
        sub.setGravity(android.view.Gravity.CENTER);
        card.addView(sub);
        root.addView(card);

        if (tool.equals("Stopwatch")) {
            setupStopwatch(root);
        } else if (tool.equals("Timer") || tool.equals("Countdown")) {
            setupTimer(root);
        } else if (tool.equals("Pomodoro Timer")) {
            setupPomodoro(root);
        } else if (tool.equals("World Clock")) {
            setupWorldClock(root);
        } else if (tool.equals("Alarm Clock")) {
            setupAlarm(root);
        }
        setContentView(Ui.scroll(this, root));
    }

    // ---------- stopwatch ----------
    void setupStopwatch(LinearLayout root) {
        ticker = new Runnable() {
            @Override
            public void run() {
                if (running) {
                    elapsed = System.currentTimeMillis() - startAt;
                    big.setText(fmtStopwatch(elapsed));
                    handler.postDelayed(this, 53);
                }
            }
        };
        Button start = Ui.button(this, "▶ START");
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!running) {
                    running = true;
                    startAt = System.currentTimeMillis() - elapsed;
                    handler.post(ticker);
                    sub.setText("Running…");
                }
            }
        });
        Button pause = Ui.button(this, "⏸ PAUSE");
        pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                running = false;
                handler.removeCallbacks(ticker);
                sub.setText("Paused at " + fmtStopwatch(elapsed));
            }
        });
        Button reset = Ui.button(this, "⟳ RESET", Ui.GOLD);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                running = false;
                handler.removeCallbacks(ticker);
                elapsed = 0;
                big.setText("00:00.0");
                sub.setText("");
            }
        });
        root.addView(start);
        root.addView(pause);
        root.addView(reset);
    }

    String fmtStopwatch(long ms) {
        long m = ms / 60000, s = (ms % 60000) / 1000, t = (ms % 1000) / 100;
        return String.format(Locale.US, "%02d:%02d.%d", m, s, t);
    }

    // ---------- timer / countdown ----------
    void setupTimer(LinearLayout root) {
        final EditText mins = Ui.input(this, "minutes", false);
        final EditText secs = Ui.input(this, "seconds", false);
        mins.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        secs.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        root.addView(mins);
        root.addView(secs);
        Button go = Ui.button(this, "▶ START TIMER");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long m = parseLong(mins), s = parseLong(secs);
                long total = m * 60000 + s * 1000;
                if (total <= 0) {
                    Toast.makeText(ClockActivity.this, "Enter minutes or seconds", Toast.LENGTH_SHORT).show();
                    return;
                }
                targetMs = System.currentTimeMillis() + total;
                running = true;
                startTicker();
                sub.setText("Timer set for " + (m > 0 ? m + "m " : "") + s + "s");
            }
        });
        Button stop = Ui.button(this, "⏹ STOP", Ui.GOLD);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                running = false;
                handler.removeCallbacks(ticker);
                big.setText("00:00.0");
                sub.setText("Stopped.");
            }
        });
        root.addView(go);
        root.addView(stop);
        big.setText("00:00");
    }

    long parseLong(EditText e) {
        String t = e.getText().toString().trim();
        return t.length() == 0 ? 0 : Long.parseLong(t);
    }

    void startTicker() {
        handler.removeCallbacks(ticker);
        ticker = new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                long left = targetMs - System.currentTimeMillis();
                if (left <= 0) {
                    running = false;
                    big.setText("00:00");
                    sub.setText("⏰ TIME'S UP!");
                    beep();
                    return;
                }
                big.setText(String.format(Locale.US, "%02d:%02d", left / 60000, (left % 60000) / 1000));
                handler.postDelayed(this, 250);
            }
        };
        handler.post(ticker);
    }

    // ---------- pomodoro ----------
    boolean breakPhase;

    void setupPomodoro(LinearLayout root) {
        sub.setText("25 min focus / 5 min break. Classic Pomodoro.");
        Button focus = Ui.button(this, "🍅 START 25 MIN FOCUS");
        focus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                breakPhase = false;
                targetMs = System.currentTimeMillis() + 25 * 60000;
                running = true;
                startTicker();
                sub.setText("Focus! 25 minutes. No phone 📵");
            }
        });
        Button rest = Ui.button(this, "☕ START 5 MIN BREAK");
        rest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                breakPhase = true;
                targetMs = System.currentTimeMillis() + 5 * 60000;
                running = true;
                startTicker();
                sub.setText("Break time. Relax ☕");
            }
        });
        Button stop = Ui.button(this, "⏹ STOP", Ui.GOLD);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                running = false;
                handler.removeCallbacks(ticker);
                big.setText("00:00");
                sub.setText("");
            }
        });
        root.addView(focus);
        root.addView(rest);
        root.addView(stop);
    }

    // ---------- world clock ----------
    void setupWorldClock(LinearLayout root) {
        final String[][] zones = {
                {"YANGON", "Asia/Yangon"}, {"LONDON", "Europe/London"}, {"PARIS", "Europe/Paris"},
                {"NEW YORK", "America/New_York"}, {"LOS ANGELES", "America/Los_Angeles"},
                {"TOKYO", "Asia/Tokyo"}, {"SINGAPORE", "Asia/Singapore"}, {"SYDNEY", "Australia/Sydney"},
                {"DUBAI", "Asia/Dubai"}, {"BANGKOK", "Asia/Bangkok"}, {"SEOUL", "Asia/Seoul"},
                {"KOLKATA", "Asia/Kolkata"}
        };
        final LinearLayout list = Ui.card(this);
        root.addView(list);
        ticker = new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss  (EEE)", Locale.US);
                StringBuilder sb = new StringBuilder();
                for (String[] z : zones) {
                    f.setTimeZone(TimeZone.getTimeZone(z[1]));
                    sb.append(z[0]).append('\n').append("   ").append(f.format(new Date())).append('\n');
                }
                SimpleDateFormat lf = new SimpleDateFormat("HH:mm:ss", Locale.US);
                big.setText(lf.format(new Date()));
                sub.setText("Local time — updating live");
                ((TextView) list.getChildAt(0)).setText(sb.toString());
                handler.postDelayed(this, 1000);
            }
        };
        TextView tv = Ui.text(this, "", 12, Ui.TEXT, false);
        list.addView(tv);
        big.setText("--:--:--");
        handler.post(ticker);
    }

    // ---------- alarm ----------
    void setupAlarm(LinearLayout root) {
        final EditText hour = Ui.input(this, "hour 0-23", false);
        final EditText minute = Ui.input(this, "minute 0-59", false);
        hour.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        minute.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        root.addView(hour);
        root.addView(minute);
        Button set = Ui.button(this, "⏰ SET ALARM");
        set.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    int h = (int) parseLong(hour), m = (int) parseLong(minute);
                    if (h < 0 || h > 23 || m < 0 || m > 59) throw new Exception("Hour 0-23, minute 0-59");
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    c.set(java.util.Calendar.HOUR_OF_DAY, h);
                    c.set(java.util.Calendar.MINUTE, m);
                    c.set(java.util.Calendar.SECOND, 0);
                    if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(java.util.Calendar.DAY_OF_MONTH, 1);
                    AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    Intent it = new Intent(ClockActivity.this, AlarmReceiver.class);
                    PendingIntent pi = PendingIntent.getBroadcast(ClockActivity.this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT);
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
                    SimpleDateFormat f = new SimpleDateFormat("HH:mm  d MMM", Locale.US);
                    sub.setText("Alarm set for " + f.format(c.getTime()) + "\nThe phone will ring at that time.");
                    Toast.makeText(ClockActivity.this, "Alarm set: " + String.format(Locale.US, "%02d:%02d", h, m), Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(ClockActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
        Button cancel = Ui.button(this, "✖ CANCEL ALARM", Ui.GOLD);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                Intent it = new Intent(ClockActivity.this, AlarmReceiver.class);
                PendingIntent pi = PendingIntent.getBroadcast(ClockActivity.this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT);
                am.cancel(pi);
                sub.setText("Alarm cancelled.");
            }
        });
        root.addView(set);
        root.addView(cancel);
    }

    void beep() {
        try {
            if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 1500);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tool.equals("World Clock")) {
            handler.removeCallbacks(ticker);
        }
        // stopwatch/timer keep logical state; ticker resumes cheaply
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tool.equals("World Clock") && ticker != null) {
            handler.post(ticker);
        } else if (running && (tool.equals("Timer") || tool.equals("Countdown") || tool.equals("Pomodoro Timer"))) {
            startTicker();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tone != null) {
            tone.release();
            tone = null;
        }
    }
}
