package com.gikoko.gitoolbox;

import android.app.Activity;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Touch Test / Multi-Touch Test / Brightness Test / Speaker Test. */
public class ScreenTestActivity extends Activity {

    private String tool;
    private TextView main, info;
    private int touches, maxPointers;
    private ToneGenerator tone;

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

        LinearLayout card = Ui.card(this);
        main = Ui.text(this, "", 15, Ui.GREEN, true);
        main.setTypeface(android.graphics.Typeface.MONOSPACE);
        card.addView(main);
        info = Ui.text(this, "", 12, Ui.DIM, false);
        card.addView(info);
        root.addView(card);

        if (tool.equals("Touch Test") || tool.equals("Multi-Touch Test")) {
            setupTouch(root);
        } else if (tool.equals("Brightness Test")) {
            setupBrightness(root);
        } else {
            setupSpeaker(root);
        }
        setContentView(Ui.scroll(this, root));
    }

    void setupTouch(LinearLayout root) {
        View pad = new View(this);
        pad.setBackground(Ui.roundStroke(0xFF071B12, 0xFF1E4B38, 12, this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 260));
        lp.topMargin = Ui.dp(this, 10);
        pad.setLayoutParams(lp);
        pad.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                touches++;
                if (ev.getPointerCount() > maxPointers) maxPointers = ev.getPointerCount();
                StringBuilder sb = new StringBuilder();
                sb.append("POINTERS: ").append(ev.getPointerCount()).append("   (max ").append(maxPointers).append(")\n");
                for (int i = 0; i < ev.getPointerCount(); i++) {
                    sb.append(String.format(Locale.US, "#%d  x=%d  y=%d%n", ev.getPointerId(i),
                            (int) ev.getX(i), (int) ev.getY(i)));
                }
                main.setText(sb.toString());
                return true;
            }
        });
        root.addView(pad);
        main.setText("TOUCH PAD\n\nTap and drag here.\nTouch count and coordinates appear live.");
        info.setText(tool.equals("Multi-Touch Test")
                ? "Use two or more fingers at once to test multi-touch."
                : "Single touch test — drag your finger across the pad.");
        Button reset = Ui.button(this, "⟳ RESET COUNTERS");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                touches = 0;
                maxPointers = 0;
                main.setText("Counters reset.\nTouch the pad again.");
            }
        });
        root.addView(reset);
    }

    void setupBrightness(LinearLayout root) {
        final WindowManager wm = getWindowManager();
        final android.content.ContentResolver cr = getContentResolver();
        try {
            android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        } catch (Exception ignored) {
        }
        main.setText("Current brightness: " + curBrightness() + " / 255");
        info.setText("Use the buttons to step brightness. If buttons do nothing, your phone blocks apps from changing brightness.");
        final int[] val = {curBrightness()};
        Button up = Ui.button(this, "☀ BRIGHTER (+20)");
        up.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                val[0] = Math.min(255, val[0] + 20);
                setBrightness(val[0]);
            }
        });
        Button down = Ui.button(this, "🌙 DIMMER (−20)");
        down.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                val[0] = Math.max(2, val[0] - 20);
                setBrightness(val[0]);
            }
        });
        Button max = Ui.button(this, "⚡ MAX", Ui.GOLD);
        max.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                val[0] = 255;
                setBrightness(255);
            }
        });
        root.addView(up);
        root.addView(down);
        root.addView(max);
    }

    int curBrightness() {
        try {
            return android.provider.Settings.System.getInt(getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception e) {
            return 128;
        }
    }

    void setBrightness(int v) {
        try {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = Math.max(0.02f, v / 255f);
            getWindow().setAttributes(lp);
            android.provider.Settings.System.putInt(getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS, v);
            main.setText("Brightness: " + v + " / 255");
        } catch (Exception e) {
            main.setText("Brightness: " + v + "\n(system refused persist — check notification bar brightness)");
        }
    }

    void setupSpeaker(LinearLayout root) {
        main.setText("Plays test tones through your speaker.");
        info.setText("Check volume is up. Each button plays a different frequency.");
        Button b1 = Ui.button(this, "🔊 PLAY 440 Hz (A4)");
        b1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(440, 800);
            }
        });
        Button b2 = Ui.button(this, "🔊 PLAY 1 kHz");
        b2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(1000, 800);
            }
        });
        Button b3 = Ui.button(this, "🔊 PLAY 6 kHz (tweeter)");
        b3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(6000, 800);
            }
        });
        Button b4 = Ui.button(this, "🔊 PLAY 200 Hz (bass)");
        b4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(200, 800);
            }
        });
        root.addView(b1);
        root.addView(b2);
        root.addView(b3);
        root.addView(b4);
    }

    void play(int hz, int ms) {
        try {
            if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 90);
            tone.startTone(hz >= 1000 ? ToneGenerator.TONE_PROP_BEEP2 : ToneGenerator.TONE_PROP_BEEP, ms);
            main.setText("Playing " + hz + " Hz …");
        } catch (Exception e) {
            Toast.makeText(this, "Tone error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
