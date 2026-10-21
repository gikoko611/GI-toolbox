package com.gikoko.gitoolbox;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v0.0.1 Media Lab — cyber audio suite, 100% native:
 * Sound Generator (5 waveforms, 40 Hz – 20 kHz), DTMF Tone Generator,
 * Ultrasonic Generator, Hearing Test, White/Pink Noise, Alarm Siren,
 * Morse Flasher (beeps + torch + screen flash), Text to Speech.
 */
public class MediaLabActivity extends Activity {

    private String tool;
    private LinearLayout out;
    private Handler h = new Handler();

    // streaming tone engine
    private AudioTrack track;
    private Thread genThread;
    private volatile boolean playing = false;
    private volatile int wave = 0;          // 0 sine 1 square 2 triangle 3 saw 4 white 5 pink
    private volatile double freq = 440.0;
    private volatile double vol = 0.55;
    private volatile boolean siren = false;
    private volatile boolean sweepAuto = false;

    // tts
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private boolean myanmarVoice = false;

    // morse
    private View flashView;
    private volatile boolean morseRunning = false;
    private AudioTrack morseTrack;

    private static final String[] MORSE = {
            ".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..", ".---",
            "-.-", ".-..", "--", "-.", "---", ".--.", "--.-", ".-.", "...", "-",
            "..-", "...-", ".--", "-..-", "-.--", "--..",
            "-----", ".----", "..---", "...--", "....-", ".....", "-....", "--...", "---..", "----."
    };

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
        LinearLayout br = Ui.row(this);
        br.addView(Ui.badge(this, "● MEDIA LAB · CYBER 3D", Ui.CYAN));
        root.addView(br);
        Ui.reveal(br);

        out = new LinearLayout(this);
        out.setOrientation(LinearLayout.VERTICAL);
        root.addView(out);

        setContentView(Ui.scroll(this, root));
        dispatch();
    }

    void dispatch() {
        if (tool.equals("Sound Generator")) soundGenerator();
        else if (tool.equals("Tone Generator")) toneGenerator();
        else if (tool.equals("Ultrasonic Generator")) ultrasonic();
        else if (tool.equals("Hearing Test")) hearingTest();
        else if (tool.equals("White Noise")) whiteNoise();
        else if (tool.equals("Alarm Siren")) alarmSiren();
        else if (tool.equals("Morse Flasher")) morseFlasher();
        else textToSpeech();
    }

    // ------------------------------------------------------------- tone engine

    void startEngine() {
        stopEngine();
        final int sr = 44100;
        try {
            track = new AudioTrack(AudioManager.STREAM_MUSIC, sr, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, sr / 2, AudioTrack.MODE_STREAM);
            track.play();
        } catch (Exception e) {
            toast("Audio start failed");
            return;
        }
        playing = true;
        genThread = new Thread(new Runnable() {
            @Override
            public void run() {
                short[] buf = new short[2048];
                double phase = 0;
                double pink = 0;
                long n = 0;
                while (playing) {
                    for (int i = 0; i < buf.length; i++) {
                        double f = freq;
                        if (siren) {
                            f = 780 + 420 * Math.sin(2 * Math.PI * (n / (double) sr) * 0.55);
                        } else if (sweepAuto) {
                            double t = (n / (double) sr) % 25.0;
                            f = 250 * Math.pow(64.0, t / 25.0);
                        }
                        double fr = phase - Math.floor(phase);
                        double v;
                        if (wave == 1) v = fr < 0.5 ? 1 : -1;
                        else if (wave == 2) v = 4 * Math.abs(fr - 0.5) - 1;
                        else if (wave == 3) v = 2 * fr - 1;
                        else if (wave == 4) v = Math.random() * 2 - 1;
                        else if (wave == 5) {
                            pink = 0.985 * pink + 0.055 * (Math.random() * 2 - 1);
                            v = pink * 6.5;
                            if (v > 1) v = 1;
                            if (v < -1) v = -1;
                        } else v = Math.sin(2 * Math.PI * phase);
                        phase += f / sr;
                        buf[i] = (short) Math.round(v * vol * 16000);
                        n++;
                    }
                    int wrote = track.write(buf, 0, buf.length);
                    if (wrote < 0) break;
                }
            }
        });
        genThread.start();
    }

    void stopEngine() {
        playing = false;
        siren = false;
        sweepAuto = false;
        try {
            if (genThread != null) genThread.join(300);
        } catch (Exception ignored) {
        }
        genThread = null;
        try {
            if (track != null) {
                track.stop();
                track.release();
            }
        } catch (Exception ignored) {
        }
        track = null;
    }

    /** One-shot dual/single tone burst (DTMF, beeps). */
    void burst(final double f1, final double f2, double seconds, final String label) {
        final int sr = 44100;
        final int len = (int) (sr * seconds);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    short[] b = new short[len];
                    for (int i = 0; i < len; i++) {
                        double t = i / (double) sr;
                        double v = 0.5 * Math.sin(2 * Math.PI * f1 * t);
                        if (f2 > 0) v += 0.5 * Math.sin(2 * Math.PI * f2 * t);
                        double env = Math.min(1.0, Math.min(t / 0.006, (len / (double) sr - t) / 0.02));
                        b[i] = (short) Math.round(v * env * vol * 16000);
                    }
                    AudioTrack t = new AudioTrack(AudioManager.STREAM_MUSIC, sr,
                            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                            len * 2, AudioTrack.MODE_STATIC);
                    t.write(b, 0, len);
                    t.play();
                    Thread.sleep(len * 1000L / sr + 120);
                    t.stop();
                    t.release();
                    if (label != null) toast(label);
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    // ------------------------------------------------------------- Sound Generator

    void soundGenerator() {
        wave = 0;
        intro("Studio tone generator: pick a waveform, drag the frequency and volume, then START. "
                + "Sine, square, triangle, sawtooth — 40 Hz to 20 kHz.");

        final TextView freqLabel = Ui.text(this, freqLabel(), 14, Ui.CYAN, true);
        out.addView(labeled("FREQUENCY", freqLabel));

        SeekBar fs = new SeekBar(this);
        fs.setMax(100);
        fs.setProgress(45);
        fs.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                freq = 40 * Math.pow(500.0, p / 100.0);
                freqLabel.setText(freqLabel());
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        out.addView(fs);

        final TextView volLabel = Ui.text(this, "VOLUME 55%", 12, Ui.GOLD, true);
        out.addView(labeled("LEVEL", volLabel));
        SeekBar vs = new SeekBar(this);
        vs.setMax(100);
        vs.setProgress(55);
        vs.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                vol = p / 100.0;
                volLabel.setText("VOLUME " + p + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        out.addView(vs);

        String[] names = {"SINE", "SQUARE", "TRIANGLE", "SAW"};
        LinearLayout r = Ui.row(this);
        for (int i = 0; i < names.length; i++) {
            final int w = i;
            Button b = Ui.button(this, names[i], i == 0 ? Ui.CYAN : Ui.GREEN);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    wave = w;
                    siren = false;
                    toast(names[w] + " wave");
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) lp.leftMargin = Ui.dp(this, 6);
            b.setLayoutParams(lp);
            r.addView(b);
        }
        out.addView(pad(r));

        startStop();
    }

    String freqLabel() {
        double f = freq;
        return f >= 1000 ? String.format(Locale.US, "%.2f kHz", f / 1000.0) : String.format(Locale.US, "%.0f Hz", f);
    }

    void startStop() {
        LinearLayout r = Ui.row(this);
        Button st = Ui.button(this, "▶ START", Ui.GREEN);
        st.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startEngine();
                toast("Playing");
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        st.setLayoutParams(lp);
        r.addView(st);
        Button sp = Ui.button(this, "■ STOP", Ui.RED);
        sp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopEngine();
                stopMorse();
                toast("Stopped");
            }
        });
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp2.leftMargin = Ui.dp(this, 8);
        sp.setLayoutParams(lp2);
        r.addView(sp);
        out.addView(pad(r));
    }

    // ------------------------------------------------------------- Tone Generator (DTMF)

    void toneGenerator() {
        intro("Phone-line DTMF tones: tap the keypad to hear the exact dual-tone pair used by "
                + "telephone systems worldwide. Useful for testing speakers and DTMF decoders.");
        final TextView disp = Ui.text(this, "—", 30, Ui.CYAN, true);
        disp.setGravity(Gravity.CENTER);
        LinearLayout dc = Ui.card(this);
        dc.addView(disp);
        out.addView(pad(dc));

        String[][] keys = {
                {"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"*", "0", "#"}
        };
        double[][] lo = {{697, 697, 697}, {770, 770, 770}, {852, 852, 852}, {941, 941, 941}};
        double[][] hi = {{1209, 1336, 1477}, {1209, 1336, 1477}, {1209, 1336, 1477}, {1209, 1336, 1477}};
        for (int row = 0; row < 4; row++) {
            LinearLayout r = Ui.row(this);
            for (int col = 0; col < 3; col++) {
                final String k = keys[row][col];
                final double f1 = lo[row][col];
                final double f2 = hi[row][col];
                Button b = Ui.button(this, k, Ui.GREEN);
                b.setTextSize(18);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        disp.setText(k);
                        Ui.flash(disp);
                        burst(f1, f2, 0.30, null);
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                if (col > 0) lp.leftMargin = Ui.dp(this, 6);
                b.setLayoutParams(lp);
                r.addView(b);
            }
            out.addView(pad(r));
        }
    }

    // ------------------------------------------------------------- Ultrasonic

    void ultrasonic() {
        wave = 0;
        intro("High-frequency tones above human hearing (15–21 kHz). Young ears may still hear the "
                + "lower ones. Note: ultrasonic 'mosquito repeller' claims are not scientifically proven — "
                + "keep volume low; dogs and cats hear these clearly.");
        String[] names = {"15 kHz", "17 kHz", "19 kHz", "21 kHz"};
        final double[] fr = {15000, 17000, 19000, 21000};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            Button b = Ui.button(this, names[i], Ui.CYAN);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    freq = fr[idx];
                    wave = 0;
                    siren = false;
                    sweepAuto = false;
                    startEngine();
                    toast(names[idx] + " playing");
                }
            });
            out.addView(pad(b));
        }
        startStop();
    }

    // ------------------------------------------------------------- Hearing Test

    void hearingTest() {
        wave = 0;
        intro("Screen your hearing: pick a preset frequency or run the auto sweep. Start at LOW volume, "
                + "only increase until barely audible. This is a rough guide, not a medical test.");
        int[] fr = {250, 500, 1000, 2000, 4000, 6000, 8000, 12000, 15000};
        LinearLayout r = null;
        for (int i = 0; i < fr.length; i++) {
            if (i % 3 == 0) {
                r = Ui.row(this);
                out.addView(pad(r));
            }
            final double f = fr[i];
            Button b = Ui.button(this, f >= 1000 ? ((int) (f / 1000)) + "kHz" : ((int) f) + "Hz", Ui.GREEN);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    freq = f;
                    wave = 0;
                    siren = false;
                    sweepAuto = false;
                    startEngine();
                    toast("Playing " + freqLabel());
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            lp.leftMargin = Ui.dp(this, 4);
            b.setLayoutParams(lp);
            r.addView(b);
        }
        Button sw = Ui.button(this, "▶ AUTO SWEEP 250Hz → 16kHz", Ui.MAGENTA);
        sw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wave = 0;
                siren = false;
                sweepAuto = true;
                startEngine();
                toast("Sweeping — raise volume only until you barely hear it");
            }
        });
        out.addView(pad(sw));
        startStop();
    }

    // ------------------------------------------------------------- White Noise

    void whiteNoise() {
        intro("Masking noise for sleep, focus and tinnitus relief. WHITE is harsh and bright; "
                + "PINK is softer, energy-balanced — most people prefer it for sleeping.");
        Button w = Ui.button(this, "▶ WHITE NOISE", Ui.CYAN);
        w.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wave = 4;
                siren = false;
                sweepAuto = false;
                startEngine();
                toast("White noise on");
            }
        });
        out.addView(pad(w));
        Button p = Ui.button(this, "▶ PINK NOISE", Ui.MAGENTA);
        p.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wave = 5;
                siren = false;
                sweepAuto = false;
                startEngine();
                toast("Pink noise on");
            }
        });
        out.addView(pad(p));
        startStop();
    }

    // ------------------------------------------------------------- Alarm Siren

    void alarmSiren() {
        wave = 0;
        intro("Classic rising-falling emergency siren. Loud by design — hold the phone up, "
                + "or pair it with Find My Phone to locate a lost handset.");
        Button go = Ui.button(this, "🚨 START SIREN", Ui.RED);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                siren = true;
                wave = 0;
                sweepAuto = false;
                startEngine();
                toast("Siren on");
            }
        });
        out.addView(pad(go));
        startStop();
    }

    // ------------------------------------------------------------- Morse Flasher

    void morseFlasher() {
        intro("Type text and play it as real Morse code: 700 Hz keyed beeps + torch flashes + "
                + "screen flash. dit = 120 ms · dah = 3 dits. Signal across a room without sound.");

        flashView = new View(this);
        flashView.setBackground(Ui.round(Ui.CARD, 12, this));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 120));
        flp.topMargin = Ui.dp(this, 8);
        flashView.setLayoutParams(flp);
        out.addView(flashView);

        final EditText msg = Ui.input(this, "Message e.g. SOS", false);
        msg.setTag("m");
        out.addView(pad(msg));

        LinearLayout r = Ui.row(this);
        Button play = Ui.button(this, "▶ PLAY MORSE", Ui.GREEN);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText m = (EditText) out.findViewWithTag("m");
                playMorse(m.getText().toString().trim().toUpperCase());
            }
        });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        play.setLayoutParams(pl);
        r.addView(play);
        Button stop = Ui.button(this, "■ STOP", Ui.RED);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopMorse();
                toast("Stopped");
            }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        sl.leftMargin = Ui.dp(this, 8);
        stop.setLayoutParams(sl);
        r.addView(stop);
        out.addView(pad(r));

        Button sos = Ui.button(this, "⚡ QUICK SOS", Ui.GOLD);
        sos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playMorse("SOS");
            }
        });
        out.addView(pad(sos));
    }

    String morseFor(char ch) {
        if (ch >= 'A' && ch <= 'Z') return MORSE[ch - 'A'];
        if (ch >= '0' && ch <= '9') return MORSE[ch - '0' + 26];
        return null;
    }

    void playMorse(String text) {
        stopMorse();
        if (text.length() == 0) {
            toast("Type a message first");
            return;
        }
        final int unit = 120; // ms
        final List<long[]> segs = new ArrayList<>(); // {0=off/1=on, ms}
        StringBuilder shown = new StringBuilder();
        String words = text.replaceAll("[^A-Z0-9 ]", "");
        if (words.length() == 0) {
            toast("Only letters A-Z and digits can be sent");
            return;
        }
        for (int w = 0; w < words.length(); w++) {
            char ch = words.charAt(w);
            if (ch == ' ') {
                segs.add(new long[]{0, unit * 4L}); // extra word gap (total 7)
                shown.append(" / ");
                continue;
            }
            String code = morseFor(ch);
            if (code == null) continue;
            for (int i = 0; i < code.length(); i++) {
                segs.add(new long[]{1, code.charAt(i) == '.' ? unit : unit * 3L});
                segs.add(new long[]{0, unit});
            }
            segs.add(new long[]{0, unit * 2L}); // letter gap (total 3)
            shown.append(code).append(' ');
        }
        final String codeStr = shown.toString().trim();

        // build audio
        final int sr = 44100;
        long totalMs = 0;
        for (long[] s : segs) totalMs += s[1];
        final int len = (int) (totalMs * sr / 1000);
        if (len < sr / 2 || len > sr * 45) {
            toast("Message too long");
            return;
        }
        final short[] pcm = new short[len];
        int pos = 0;
        for (long[] s : segs) {
            int n = (int) (s[1] * sr / 1000);
            for (int i = 0; i < n && pos < len; i++) {
                double t = i / (double) sr;
                double v = s[0] == 1 ? Math.sin(2 * Math.PI * 700 * t) : 0;
                pcm[pos++] = (short) Math.round(v * vol * 20000);
            }
        }

        morseRunning = true;
        try {
            morseTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sr, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, len * 2, AudioTrack.MODE_STATIC);
            morseTrack.write(pcm, 0, len);
            morseTrack.play();
        } catch (Exception e) {
            toast("Audio failed");
            morseRunning = false;
            return;
        }
        toast("Sending: " + codeStr);

        // torch + screen flash schedule
        new Thread(new Runnable() {
            @Override
            public void run() {
                long mark = 0;
                for (long[] s : segs) {
                    if (!morseRunning) break;
                    final boolean on = s[0] == 1;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                flashView.setBackground(Ui.round(on ? Ui.GOLD : Ui.CARD, 12, MediaLabActivity.this));
                                if (on) Ui.flash(flashView);
                            } catch (Exception ignored) {
                            }
                        }
                    });
                    torch(on);
                    mark += s[1];
                    try {
                        Thread.sleep(s[1]);
                    } catch (Exception ignored) {
                    }
                }
                torch(false);
                morseRunning = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            flashView.setBackground(Ui.round(Ui.CARD, 12, MediaLabActivity.this));
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
        }).start();
    }

    void stopMorse() {
        morseRunning = false;
        try {
            if (morseTrack != null) {
                morseTrack.stop();
                morseTrack.release();
            }
        } catch (Exception ignored) {
        }
        morseTrack = null;
        torch(false);
    }

    void torch(boolean on) {
        try {
            android.hardware.camera2.CameraManager cm = (android.hardware.camera2.CameraManager)
                    getSystemService(CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                if (cm.getCameraCharacteristics(id).get(
                        android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)) {
                    cm.setTorchMode(id, on);
                    break;
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------- Text to Speech

    void textToSpeech() {
        intro("Turn text into spoken audio with adjustable rate and pitch. English voices are "
                + "built into most phones; a Myanmar (my) voice shows as MYANMAR if your system has one.");

        final EditText msg = Ui.input(this, "Type anything to speak…", true);
        msg.setText("Hello from GIToolbox. မင်္ဂလာပါ။");
        msg.setTag("t");
        out.addView(pad(msg));

        LinearLayout status = Ui.card(this);
        final TextView st = Ui.text(this, "TTS loading…", 12, Ui.GOLD, true);
        status.addView(st);
        out.addView(pad(status));

        final TextView rateLabel = Ui.text(this, "RATE 1.5×", 12, Ui.CYAN, true);
        out.addView(labeled("SPEED", rateLabel));
        final SeekBar rs = new SeekBar(this);
        rateBar = rs;
        rs.setMax(30);
        rs.setProgress(10);
        rs.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                rateLabel.setText("RATE " + (5 + p) / 10.0 + "×");
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        out.addView(rs);

        final TextView pitchLabel = Ui.text(this, "PITCH 1.5×", 12, Ui.CYAN, true);
        out.addView(labeled("TONE", pitchLabel));
        final SeekBar ps = new SeekBar(this);
        pitchBar = ps;
        ps.setMax(30);
        ps.setProgress(10);
        ps.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                pitchLabel.setText("PITCH " + (5 + p) / 10.0 + "×");
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        out.addView(ps);

        LinearLayout r = Ui.row(this);
        Button speak = Ui.button(this, "🔊 SPEAK", Ui.GREEN);
        speak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!ttsReady) {
                    toast("TTS still loading");
                    return;
                }
                EditText m = (EditText) out.findViewWithTag("t");
                String t = m.getText().toString().trim();
                if (t.length() == 0) {
                    toast("Type something first");
                    return;
                }
                try {
                    float rt = (5 + rs.getProgress()) / 10f;
                    float pt = (5 + ps.getProgress()) / 10f;
                    tts.setSpeechRate(rt);
                    tts.setPitch(pt);
                } catch (Exception ignored) {
                }
                speak(t);
                Ui.flash(v);
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        speak.setLayoutParams(slp);
        r.addView(speak);
        Button stop = Ui.button(this, "■ STOP", Ui.RED);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (tts != null) tts.stop();
                } catch (Exception ignored) {
                }
            }
        });
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        stp.leftMargin = Ui.dp(this, 8);
        stop.setLayoutParams(stp);
        r.addView(stop);
        out.addView(pad(r));

        Button myBtn = Ui.button(this, "🇲🇲 TRY MYANMAR VOICE", Ui.GOLD);
        myBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!ttsReady) {
                    toast("TTS still loading");
                    return;
                }
                if (myanmarVoice) {
                    speak("မင်္ဂလာပါ။ GIToolbox မှ နှုတ်ခွန်းဆော်ပါ။");
                } else {
                    toast("No Myanmar voice installed. Install a 'my' TTS voice in system settings");
                }
            }
        });
        out.addView(pad(myBtn));

        final TextView stF = st;
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        tts.setLanguage(Locale.US);
                        int r = tts.setLanguage(new Locale("my"));
                        myanmarVoice = r != TextToSpeech.LANG_MISSING_DATA
                                && r != TextToSpeech.LANG_NOT_SUPPORTED;
                        tts.setLanguage(Locale.US);
                    } catch (Exception ignored) {
                    }
                    ttsReady = true;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stF.setText(myanmarVoice ? "TTS READY · MYANMAR VOICE FOUND"
                                    : "TTS READY · ENGLISH VOICE");
                            stF.setTextColor(myanmarVoice ? Ui.GREEN : Ui.CYAN);
                        }
                    });
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stF.setText("TTS NOT AVAILABLE on this device");
                            stF.setTextColor(Ui.RED);
                        }
                    });
                }
            }
        });
    }

    private SeekBar rateBar;
    private SeekBar pitchBar;

    void speak(String text) {
        try {
            if (rateBar != null) tts.setSpeechRate((5 + rateBar.getProgress()) / 10f);
            if (pitchBar != null) tts.setPitch((5 + pitchBar.getProgress()) / 10f);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gitoolbox");
        } catch (Exception e) {
            toast("Speak failed");
        }
    }

    // ------------------------------------------------------------- helpers

    void intro(String s) {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, s, 12, Ui.TEXT, false));
        out.addView(c);
        Ui.reveal(c);
    }

    View labeled(String label, TextView value) {
        LinearLayout l = Ui.card(this);
        l.addView(Ui.text(this, label, 10, Ui.DIM, true));
        l.addView(value);
        return l;
    }

    View pad(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 8);
        v.setLayoutParams(lp);
        return v;
    }

    void toast(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MediaLabActivity.this, s, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopEngine();
        stopMorse();
        h.removeCallbacksAndMessages(null);
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
        } catch (Exception ignored) {
        }
    }
}
