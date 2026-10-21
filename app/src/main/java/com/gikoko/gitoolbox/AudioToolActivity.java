package com.gikoko.gitoolbox;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Native audio tools: recorder (MediaRecorder/AAC) and metronome (ToneGenerator). */
public class AudioToolActivity extends Activity {

    private String tool;
    private LinearLayout box;
    private TextView status;

    private MediaRecorder rec;
    private MediaPlayer player;
    private File recFile;
    private boolean recording;

    private final Handler h = new Handler();
    private boolean metroOn;
    private ToneGenerator tone;
    private int beat;
    private int bpm = 120;
    private long recStart;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!metroOn) return;
            if (beat % 4 == 0) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80);
            else tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 50);
            beat++;
            h.postDelayed(tick, 60000L / Math.max(30, bpm));
        }
    };

    private final Runnable recTimer = new Runnable() {
        @Override
        public void run() {
            if (!recording) return;
            long secs = (System.currentTimeMillis() - recStart) / 1000L;
            if (status != null) status.setText("Recording... " + (secs / 60) + ":" + String.format("%02d", Long.valueOf(secs % 60)));
            h.postDelayed(recTimer, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tool = getIntent() == null ? null : getIntent().getStringExtra("tool");
        if (tool == null) tool = "Audio Recorder";
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.header(this, tool, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));
        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        sc.addView(box);
        root.addView(sc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        status = Ui.text(this, "", 14, Ui.TEXT, false);
        build();
    }

    private void build() {
        if (tool.equals("Audio Recorder")) buildRecorder();
        else if (tool.equals("Metronome")) buildMetronome();
        else {
            LinearLayout c = Ui.card(this);
            c.addView(Ui.text(this, tool, 16, Ui.GOLD, true));
            c.addView(Ui.text(this, "This tool ships with the next APK update.", 14, Ui.TEXT, false));
            box.addView(c);
        }
    }

    private View spacer() {
        View s = new View(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.dp(this, 10)));
        return s;
    }

    private void toast(String s) {
        Toast.makeText(AudioToolActivity.this, s, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------ recorder

    private void buildRecorder() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Record with the microphone, then play or save the file.", 13, Ui.DIM, false));
        status.setText("Ready");
        c.addView(status);
        box.addView(c);
        box.addView(spacer());
        Button recBtn = Ui.button(this, "Record", Ui.GOLD);
        recBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRec();
            }
        });
        Button stopBtn = Ui.button(this, "Stop");
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRec();
            }
        });
        Button playBtn = Ui.button(this, "Play");
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playRec();
            }
        });
        Button saveBtn = Ui.button(this, "Save Recording", Ui.GOLD);
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveRec();
            }
        });
        box.addView(recBtn);
        box.addView(stopBtn);
        box.addView(playBtn);
        box.addView(saveBtn);
    }

    private void startRec() {
        if (recording) { toast("Already recording"); return; }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 7);
            return;
        }
        try {
            stopPlayer();
            recFile = new File(getCacheDir(), "gitoolbox_rec.m4a");
            if (recFile.exists()) recFile.delete();
            rec = new MediaRecorder();
            rec.setAudioSource(MediaRecorder.AudioSource.MIC);
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            rec.setAudioEncodingBitRate(96000);
            rec.setAudioSamplingRate(44100);
            rec.setOutputFile(recFile.getAbsolutePath());
            rec.prepare();
            rec.start();
            recording = true;
            recStart = System.currentTimeMillis();
            status.setText("Recording... 0:00");
            status.setTextColor(Ui.GOLD);
            h.postDelayed(recTimer, 500);
        } catch (Exception e) {
            toast("Record failed: " + e.getMessage());
            recording = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == 7) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startRec();
            else toast("Microphone permission needed");
        }
    }

    private void stopRec() {
        if (!recording || rec == null) { toast("Not recording"); return; }
        try {
            rec.stop();
            rec.release();
        } catch (Exception e) {
            toast("Stop failed");
        }
        rec = null;
        recording = false;
        h.removeCallbacks(recTimer);
        status.setText("Recording stopped. Play or Save.");
        status.setTextColor(Ui.TEXT);
    }

    private void playRec() {
        if (recFile == null || !recFile.exists()) { toast("Record something first"); return; }
        stopPlayer();
        try {
            player = new MediaPlayer();
            player.setDataSource(recFile.getAbsolutePath());
            player.prepare();
            player.start();
            status.setText("Playing...");
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    status.setText("Playback finished");
                }
            });
        } catch (Exception e) {
            toast("Play failed: " + e.getMessage());
        }
    }

    private void stopPlayer() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignore) {
            }
            player.release();
            player = null;
        }
    }

    private void saveRec() {
        if (recFile == null || !recFile.exists()) { toast("Record something first"); return; }
        Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("audio/mp4");
        it.putExtra("android.intent.extra.TITLE", "GIToolbox_recording.m4a");
        startActivityForResult(it, 2);
    }

    // ------------------------------------------------------------ metronome

    private void buildMetronome() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Steady click track at your BPM (4 beats per measure).", 13, Ui.DIM, false));
        final EditText bpmEt = Ui.input(this, "BPM (default 120)", false);
        bpmEt.setText("120");
        c.addView(bpmEt);
        status.setText("Stopped");
        c.addView(status);
        box.addView(c);
        box.addView(spacer());
        Button startBtn = Ui.button(this, "Start", Ui.GOLD);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    bpm = Integer.parseInt(bpmEt.getText().toString().trim());
                } catch (Exception e) {
                    bpm = 120;
                }
                if (bpm < 30) bpm = 30;
                if (bpm > 300) bpm = 300;
                startMetro();
            }
        });
        Button stopBtn = Ui.button(this, "Stop");
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopMetro();
            }
        });
        box.addView(startBtn);
        box.addView(stopBtn);
    }

    private void startMetro() {
        if (metroOn) { toast("Already running"); return; }
        tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        beat = 0;
        metroOn = true;
        status.setText("Playing at " + bpm + " BPM");
        status.setTextColor(Ui.GOLD);
        h.post(tick);
    }

    private void stopMetro() {
        if (!metroOn) { toast("Not running"); return; }
        metroOn = false;
        h.removeCallbacks(tick);
        if (tone != null) {
            tone.release();
            tone = null;
        }
        status.setText("Stopped");
        status.setTextColor(Ui.TEXT);
    }

    // ------------------------------------------------------------ result

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 2 && res == RESULT_OK && data != null && recFile != null && recFile.exists()) {
            try {
                OutputStream os = getContentResolver().openOutputStream(data.getData());
                InputStream in = new FileInputStream(recFile);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                in.close();
                os.close();
                toast("Recording saved");
            } catch (Exception e) {
                toast("Save failed: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (recording) stopRec();
        stopMetro();
        stopPlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recording) {
            try {
                if (rec != null) {
                    rec.stop();
                    rec.release();
                }
            } catch (Exception ignore) {
            }
        }
        stopPlayer();
    }
}
