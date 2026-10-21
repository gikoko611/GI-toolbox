package com.gikoko.gitoolbox;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/** Native audio tools v3.0: recorder, metronome, cutter, merger, extractor, converter, player fx, MP3→WAV. */
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

    // v3 picks
    private Uri pickedAudio;
    private Uri pickedAudioB;
    private Uri pickedVideo;
    private File pendingOut;      // muxed/decoded output waiting to be saved
    private String pendingMime = "audio/mp4";
    private String pendingName = "gitoolbox.m4a";
    private LoudnessEnhancer booster;

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
        else if (tool.equals("Audio Cutter") || tool.equals("MP3 Cutter")) buildCutter();
        else if (tool.equals("Audio Merger")) buildMerger();
        else if (tool.equals("Audio Extractor")) buildExtractor();
        else if (tool.equals("Audio Converter") || tool.equals("M4A Converter")) buildConverter();
        else if (tool.equals("Speed Changer")) buildPlaybackFx("speed");
        else if (tool.equals("Voice Changer")) buildPlaybackFx("pitch");
        else if (tool.equals("Volume Booster")) buildPlaybackFx("gain");
        else if (tool.equals("MP3 → WAV")) buildMp3ToWav();
        else buildPanel();
    }

    private void buildPanel() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, tool, 16, Ui.GOLD, true));
        String msg;
        if (tool.equals("Noise Reducer")) {
            msg = "Real-time noise suppression needs a DSP engine module, which ships with the next APK update.\n\nTip: for clean recordings, use Audio Recorder in a quiet room — it records crisp 96 kbps AAC.";
        } else if (tool.equals("WAV → MP3")) {
            msg = "MP3 encoding needs a licensed encoder library, which ships with the next APK update.\n\nFor now: Audio Converter / M4A Converter repackage any audio into universal .m4a (AAC) — supported by every phone and PC.";
        } else if (tool.equals("FLAC Converter")) {
            msg = "FLAC encoding ships with the next APK update.\n\nFor lossless-quality export today, use MP3 → WAV (uncompressed PCM).";
        } else {
            msg = "This tool ships with the next APK update.";
        }
        c.addView(Ui.text(this, msg, 14, Ui.TEXT, false));
        box.addView(c);
    }

    private View spacer() {
        View s = new View(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.dp(this, 10)));
        return s;
    }

    private void toast(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(AudioToolActivity.this, s, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setStatus(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                status.setText(s);
            }
        });
    }

    private float floatOf(EditText e, float def) {
        try {
            return Float.parseFloat(e.getText().toString().trim());
        } catch (Exception ex) {
            return def;
        }
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
        if (booster != null) {
            try {
                booster.release();
            } catch (Exception ignore) {
            }
            booster = null;
        }
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
        pendingOut = recFile;
        pendingMime = "audio/mp4";
        pendingName = "GIToolbox_recording.m4a";
        Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType(pendingMime);
        it.putExtra("android.intent.extra.TITLE", pendingName);
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

    // ------------------------------------------------------------ cutter (extractor + muxer)

    private void buildCutter() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Cut a clip out of any audio file (start → end seconds). Works on the timeline — no re-encoding, so quality stays perfect.", 13, Ui.DIM, false));
        c.addView(pickAudio(20, "Pick Audio File"));
        c.addView(spacer());
        final EditText st = Ui.input(this, "Start second (e.g. 10)", false);
        st.setText("0");
        final EditText en = Ui.input(this, "End second (blank = to the end)", false);
        c.addView(st);
        c.addView(en);
        box.addView(c);
        box.addView(spacer());
        status.setText("");
        Button go = Ui.button(this, "✂ Cut Audio");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final float s = floatOf(st, 0f);
                final float e = floatOf(en, -1f);
                cutOrConvert("cut", s, e);
            }
        });
        box.addView(go);
        Button play = Ui.button(this, "▶ Play Original");
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playUri(pickedAudio);
            }
        });
        box.addView(play);
        box.addView(status);
    }

    private void cutOrConvert(final String mode, final float startSec, final float endSec) {
        if (pickedAudio == null) { toast("Pick an audio file first"); return; }
        toast("Processing…");
        new Thread() {
            @Override
            public void run() {
                try {
                    File in = copyToCache(pickedAudio, "av_in.bin");
                    File out = new File(getCacheDir(), "av_out.m4a");
                    if (out.exists()) out.delete();
                    long startUs = Math.max(0, (long) (startSec * 1000000f));
                    long endUs = endSec > 0 ? (long) (endSec * 1000000f) : Long.MAX_VALUE;
                    long cut = AudioVideoMux.mux(in, out, true, startUs, endUs, null);
                    if (cut <= 0) throw new Exception("No audio track found in that file");
                    pendingOut = out;
                    pendingMime = "audio/mp4";
                    pendingName = tool.equals("MP3 Cutter") ? "gitoolbox_cut.mp4" : "gitoolbox_cut.m4a";
                    savePending();
                } catch (final Exception e) {
                    toast("Failed: " + e.getMessage());
                }
            }
        }.start();
    }

    private void buildMerger() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Join two audio files into one (best when both are the same format, e.g. two MP3s or two M4As).", 13, Ui.DIM, false));
        c.addView(pickAudio(20, "Pick Audio A"));
        c.addView(spacer());
        c.addView(pickAudio(21, "Pick Audio B"));
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Merge A + B");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedAudio == null || pickedAudioB == null) { toast("Pick both audio files first"); return; }
                toast("Merging…");
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            File a = copyToCache(pickedAudio, "av_a.bin");
                            File b = copyToCache(pickedAudioB, "av_b.bin");
                            File out = new File(getCacheDir(), "av_out.m4a");
                            if (out.exists()) out.delete();
                            AudioVideoMux.merge(a, b, out, true);
                            pendingOut = out;
                            pendingMime = "audio/mp4";
                            pendingName = "gitoolbox_merged.m4a";
                            savePending();
                        } catch (final Exception e) {
                            toast("Merge failed: " + e.getMessage());
                        }
                    }
                }.start();
            }
        });
        box.addView(go);
    }

    private void buildExtractor() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Pull the sound track out of any video (MP4, 3GP…) and save it as an .m4a audio file.", 13, Ui.DIM, false));
        Button pick = Ui.button(this, "Pick Video File", Ui.GOLD);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("video/*");
                startActivityForResult(it, 22);
            }
        });
        c.addView(pick);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Extract Audio");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedVideo == null) { toast("Pick a video first"); return; }
                toast("Extracting…");
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            File in = copyToCache(pickedVideo, "av_in.bin");
                            File out = new File(getCacheDir(), "av_out.m4a");
                            if (out.exists()) out.delete();
                            long n = AudioVideoMux.mux(in, out, true, 0, Long.MAX_VALUE, null);
                            if (n <= 0) throw new Exception("No audio track in that video");
                            pendingOut = out;
                            pendingMime = "audio/mp4";
                            pendingName = "gitoolbox_audio.m4a";
                            savePending();
                        } catch (final Exception e) {
                            toast("Failed: " + e.getMessage());
                        }
                    }
                }.start();
            }
        });
        box.addView(go);
    }

    private void buildConverter() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Repackages any audio into a universal .m4a (AAC/MP4) container — plays on every phone, PC and smart TV.", 13, Ui.DIM, false));
        c.addView(pickAudio(20, "Pick Audio File"));
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Convert to M4A");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedAudio == null) { toast("Pick an audio file first"); return; }
                toast("Converting…");
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            File in = copyToCache(pickedAudio, "av_in.bin");
                            File out = new File(getCacheDir(), "av_out.m4a");
                            if (out.exists()) out.delete();
                            long n = AudioVideoMux.mux(in, out, true, 0, Long.MAX_VALUE, null);
                            if (n <= 0) throw new Exception("No audio track found");
                            pendingOut = out;
                            pendingMime = "audio/mp4";
                            pendingName = "gitoolbox_audio.m4a";
                            savePending();
                        } catch (final Exception e) {
                            toast("Failed: " + e.getMessage());
                        }
                    }
                }.start();
            }
        });
        box.addView(go);
    }

    // ------------------------------------------------------------ playback fx

    private void buildPlaybackFx(final String mode) {
        String desc = mode.equals("speed") ? "Play any audio slower or faster without changing the pitch."
                : mode.equals("pitch") ? "Play any audio with a higher or lower voice — chipmunk or deep."
                : "Boost quiet audio up to +20 dB while playing.";
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, desc, 13, Ui.DIM, false));
        c.addView(pickAudio(20, "Pick Audio File"));
        c.addView(spacer());
        final EditText val = Ui.input(this, mode.equals("gain") ? "Boost dB (0-20, default 10)" : "Value 0.5 - 2.0 (default 1.5)", false);
        val.setText(mode.equals("gain") ? "10" : "1.5");
        c.addView(val);
        status.setText("Ready. Pick a file and press PLAY.");
        c.addView(status);
        box.addView(c);
        box.addView(spacer());
        Button play = Ui.button(this, "▶ PLAY", Ui.GOLD);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedAudio == null) { toast("Pick an audio file first"); return; }
                stopPlayer();
                try {
                    player = new MediaPlayer();
                    player.setDataSource(AudioToolActivity.this, pickedAudio);
                    player.prepare();
                    if (mode.equals("gain")) {
                        int sid = player.getAudioSessionId();
                        booster = new LoudnessEnhancer(sid);
                        booster.setGain(Math.max(0, Math.min(20, floatOf(val, 10f))) * 100);
                        booster.setEnabled(true);
                    } else {
                        float f = floatOf(val, 1.5f);
                        if (f < 0.5f) f = 0.5f;
                        if (f > 2f) f = 2f;
                        android.media.PlaybackParams pp = new android.media.PlaybackParams();
                        if (mode.equals("speed")) pp.setSpeed(f);
                        else pp.setPitch(f);
                        player.setPlaybackParams(pp);
                    }
                    player.start();
                    status.setText("Playing… (" + val.getText().toString().trim() + ")");
                    status.setTextColor(Ui.GOLD);
                    player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            status.setText("Playback finished");
                            status.setTextColor(Ui.TEXT);
                        }
                    });
                } catch (Exception e) {
                    toast("Play failed: " + e.getMessage());
                }
            }
        });
        Button stop = Ui.button(this, "■ Stop");
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopPlayer();
                status.setText("Stopped");
                status.setTextColor(Ui.TEXT);
            }
        });
        box.addView(play);
        box.addView(stop);
    }

    private void playUri(Uri u) {
        if (u == null) { toast("Pick a file first"); return; }
        stopPlayer();
        try {
            player = new MediaPlayer();
            player.setDataSource(this, u);
            player.prepare();
            player.start();
            toast("Playing…");
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    toast("Finished");
                }
            });
        } catch (Exception e) {
            toast("Play failed: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ mp3 -> wav (MediaCodec decode)

    private void buildMp3ToWav() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Decodes any compressed audio (MP3, M4A, AAC…) into a lossless uncompressed .wav file.", 13, Ui.DIM, false));
        c.addView(pickAudio(20, "Pick Audio File"));
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Convert to WAV");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedAudio == null) { toast("Pick an audio file first"); return; }
                toast("Decoding… (can take a moment)");
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            File in = copyToCache(pickedAudio, "av_in.bin");
                            final File wav = decodeToWav(in);
                            pendingOut = wav;
                            pendingMime = "audio/wav";
                            pendingName = "gitoolbox_audio.wav";
                            savePending();
                        } catch (final Exception e) {
                            toast("Failed: " + e.getMessage());
                        }
                    }
                }.start();
            }
        });
        box.addView(go);
    }

    private File decodeToWav(File input) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(input.getAbsolutePath());
        int track = -1;
        MediaFormat fmt = null;
        for (int i = 0; i < ex.getTrackCount(); i++) {
            MediaFormat f = ex.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                track = i;
                fmt = f;
                break;
            }
        }
        if (track < 0) throw new Exception("No audio track found");
        ex.selectTrack(track);
        String mime = fmt.getString(MediaFormat.KEY_MIME);
        int channels = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
        int rate = fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(fmt, null, null, 0);
        codec.start();
        java.io.ByteArrayOutputStream pcm = new java.io.ByteArrayOutputStream();
        ByteBuffer[] inBufs = null;
        ByteBuffer[] outBufs = null;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean eos = false;
        long guard = System.currentTimeMillis();
        while (System.currentTimeMillis() - guard < 120000) {
            if (!eos) {
                int bi = codec.dequeueInputBuffer(10000);
                if (bi >= 0) {
                    ByteBuffer ib = codec.getInputBuffer(bi);
                    int size = ex.readSampleData(ib, 0);
                    if (size < 0) {
                        codec.queueInputBuffer(bi, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        eos = true;
                    } else {
                        codec.queueInputBuffer(bi, 0, size, ex.getSampleTime(), 0);
                        ex.advance();
                    }
                }
            }
            int bo = codec.dequeueOutputBuffer(info, 10000);
            if (bo >= 0) {
                ByteBuffer ob = codec.getOutputBuffer(bo);
                if (ob != null) {
                    byte[] chunk = new byte[info.size];
                    ob.get(chunk);
                    pcm.write(chunk);
                }
                codec.releaseOutputBuffer(bo, false);
            } else if (bo == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat of = codec.getOutputFormat();
                channels = of.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? of.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : channels;
                rate = of.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? of.getInteger(MediaFormat.KEY_SAMPLE_RATE) : rate;
            }
            if (eos && bo == MediaCodec.INFO_TRY_AGAIN_LATER && pcm.size() > 0) {
                // keep draining a little; break when decoder fully done
                break;
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
        }
        codec.stop();
        codec.release();
        ex.release();
        byte[] data = pcm.toByteArray();
        File out = new File(getCacheDir(), "av_out.wav");
        FileOutputStream fos = new FileOutputStream(out);
        writeWav(fos, data, channels, rate);
        fos.close();
        if (data.length == 0) throw new Exception("Could not decode that file");
        return out;
    }

    private void writeWav(OutputStream os, byte[] pcm, int channels, int rate) throws Exception {
        long dataSize = pcm.length;
        long byteRate = rate * channels * 2;
        writeAscii(os, "RIFF");
        writeIntLE(os, (int) (36 + dataSize));
        writeAscii(os, "WAVE");
        writeAscii(os, "fmt ");
        writeIntLE(os, 16);
        os.write(1); // PCM
        os.write(0);
        writeShortLE(os, channels);
        writeShortLE(os, rate);
        writeIntLE(os, (int) byteRate);
        writeShortLE(os, channels * 2);
        writeShortLE(os, 16);
        writeAscii(os, "data");
        writeIntLE(os, (int) dataSize);
        os.write(pcm);
        os.flush();
    }

    private void writeIntLE(OutputStream os, int v) throws Exception {
        os.write(v & 0xFF);
        os.write((v >> 8) & 0xFF);
        os.write((v >> 16) & 0xFF);
        os.write((v >> 24) & 0xFF);
    }

    private void writeShortLE(OutputStream os, int v) throws Exception {
        os.write(v & 0xFF);
        os.write((v >> 8) & 0xFF);
    }

    private void writeAscii(OutputStream os, String s) throws Exception {
        os.write(s.getBytes("US-ASCII"));
    }

    // ------------------------------------------------------------ shared

    private Button pickAudio(final int code, String label) {
        Button b = Ui.button(this, label, Ui.GOLD);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("audio/*");
                startActivityForResult(it, code);
            }
        });
        return b;
    }

    File copyToCache(Uri u, String name) throws Exception {
        File f = new File(getCacheDir(), name);
        if (f.exists()) f.delete();
        InputStream in = getContentResolver().openInputStream(u);
        FileOutputStream fos = new FileOutputStream(f);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        in.close();
        fos.close();
        return f;
    }

    void savePending() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType(pendingMime);
                it.putExtra("android.intent.extra.TITLE", pendingName);
                startActivityForResult(it, 2);
            }
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        Uri u = data.getData();
        if (req == 2) {
            if (pendingOut != null && pendingOut.exists()) {
                try {
                    OutputStream os = getContentResolver().openOutputStream(u);
                    FileInputStream in = new FileInputStream(pendingOut);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    in.close();
                    os.close();
                    toast("Saved ✓");
                } catch (Exception e) {
                    toast("Save failed: " + e.getMessage());
                }
            }
        } else if (req == 20) {
            pickedAudio = u;
            toast("Audio loaded ✓");
        } else if (req == 21) {
            pickedAudioB = u;
            toast("Audio B loaded ✓");
        } else if (req == 22) {
            pickedVideo = u;
            toast("Video loaded ✓");
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
