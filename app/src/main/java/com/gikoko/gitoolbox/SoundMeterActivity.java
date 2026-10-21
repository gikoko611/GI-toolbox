package com.gikoko.gitoolbox;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Live sound level meter (dB estimate from mic RMS). */
public class SoundMeterActivity extends Activity {

    private TextView dbView, hint;
    private AudioRecord record;
    private Thread worker;
    private boolean running;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final String tool = getIntent().getStringExtra("tool");
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.header(this, tool, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));

        LinearLayout card = Ui.card(this);
        dbView = Ui.text(this, "-- dB", 34, Ui.GREEN, true);
        dbView.setGravity(android.view.Gravity.CENTER);
        dbView.setTypeface(android.graphics.Typeface.MONOSPACE);
        card.addView(dbView);
        hint = Ui.text(this, "", 12, Ui.DIM, false);
        card.addView(hint);
        root.addView(card);

        Button start = Ui.button(this, "🎙 START METER");
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startMeter();
            }
        });
        root.addView(start);

        setContentView(Ui.scroll(this, root));
    }

    void startMeter() {
        if (running) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 2);
            hint.setText("Microphone permission needed. Tap START again after allowing.");
            return;
        }
        try {
            int rate = 8000;
            int minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            record = new AudioRecord(MediaRecorder.AudioSource.MIC, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuf, 2048));
            record.startRecording();
            running = true;
            hint.setText("Live measuring… speak or make a sound.");
            worker = new Thread() {
                @Override
                public void run() {
                    short[] buf = new short[2048];
                    while (running) {
                        int n = record.read(buf, 0, buf.length);
                        if (n <= 0) continue;
                        double sum = 0;
                        for (int i = 0; i < n; i++) sum += buf[i] * (double) buf[i];
                        double rms = Math.sqrt(sum / n);
                        final double db = 20 * Math.log10(rms / 32768.0) + 100; // rough calibrated estimate
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!running) return;
                                double d = Math.max(0, Math.min(120, db));
                                dbView.setText(Ui.fmt(d, 1) + " dB");
                                dbView.setTextColor(d < 50 ? Ui.GREEN : d < 75 ? Ui.GOLD : 0xFFFF5555);
                            }
                        });
                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            };
            worker.start();
        } catch (Exception e) {
            hint.setText("Mic error: " + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        running = false;
        try {
            if (record != null) {
                record.stop();
                record.release();
            }
        } catch (Exception ignored) {
        }
    }
}
