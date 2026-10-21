package com.gikoko.gitoolbox;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * v0.0.1 WiFi & Comm suite — LAN voice + messaging over one TCP link:
 * WiFi Call (full-duplex voice), Walkie-Talkie (push-to-talk), LAN Chat,
 * Clipboard Sync, Battery Remote, Find My Phone, Phone Control (LAN).
 * Both phones run GIToolbox on the same Wi-Fi. 100% native, no server needed.
 */
public class WifiCommActivity extends Activity {

    private String tool;
    private LinearLayout out;
    private TextView log;
    private LinearLayout logCard;
    private volatile boolean running = true;
    private volatile boolean connected = false;
    private volatile boolean voiceMode = false;
    private volatile boolean talking = false;
    private volatile boolean muted = false;
    private volatile boolean alarmOn = false;
    private ServerSocket ss;
    private Socket sock;
    private OutputStream outStream;
    private BufferedReader inReader;
    private AudioRecord rec;
    private AudioTrack track;
    private Thread sirenT;

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
        br.addView(Ui.badge(this, "● LAN P2P · NO SERVER", Ui.CYAN));
        root.addView(br);
        Ui.reveal(br);

        out = new LinearLayout(this);
        out.setOrientation(LinearLayout.VERTICAL);
        root.addView(out);

        logCard = Ui.card(this);
        logCard.addView(Ui.text(this, "LINK LOG", 11, Ui.DIM, true));
        log = Ui.text(this, "Ready.", 11, Ui.TEXT, false);
        logCard.addView(log);
        root.addView(logCard);

        setContentView(Ui.scroll(this, root));

        if (android.Manifest.permission.RECORD_AUDIO != null) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 5);
            }
        }
        if (tool.equals("WiFi Call")) voiceMode = true;

        intro();
        roleButtons();
    }

    void intro() {
        String s;
        if (tool.equals("WiFi Call")) {
            s = "Free voice call over your Wi-Fi — no internet, no SIM credit.\n\nHOW: on phone A tap HOST. On phone B enter A's IP and tap CONNECT. Both sides talk live. Keep this screen open during the call.";
        } else if (tool.equals("Walkie-Talkie")) {
            s = "Push-to-talk radio over Wi-Fi. Press and hold TALK to transmit; release to listen.";
        } else if (tool.equals("LAN Chat")) {
            s = "Direct text chat between two GIToolbox phones on the same Wi-Fi.";
        } else if (tool.equals("LAN Clipboard Sync")) {
            s = "Push your clipboard text straight to the other phone's clipboard.";
        } else if (tool.equals("Battery Remote")) {
            s = "Ask the other phone for its battery level and charging state.";
        } else if (tool.equals("Find My Phone")) {
            s = "Lost phone nearby? HOST on the lost phone, then RING it from your other phone — it will siren, flash its torch and vibrate until you stop it.";
        } else {
            s = "Remote-control the other phone: ring it, show a message, vibrate it, ask its battery. HOST runs on the controlled phone, this one sends the commands.";
        }
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, s, 12, Ui.TEXT, false));
        out.addView(c);
        Ui.reveal(c);
    }

    void roleButtons() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "MY IP: " + LanComm.localIp() + "   PORT: " + LanComm.PORT, 12, Ui.CYAN, true));
        out.addView(c);

        LinearLayout row = Ui.row(this);
        Button host = Ui.button(this, "📱 HOST", Ui.GREEN);
        host.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startHost();
            }
        });
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        host.setLayoutParams(hp);
        row.addView(host);
        Button client = Ui.button(this, "🔗 CONNECT", Ui.GOLD);
        client.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startClient();
            }
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        cp.leftMargin = Ui.dp(this, 8);
        client.setLayoutParams(cp);
        row.addView(client);
        out.addView(row);

        if (!voiceMode && !tool.equals("LAN Chat")) {
            final EditText ip = Ui.input(this, "Host IP e.g. 192.168.43.1", false);
            ip.setTag("ip");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 8);
            ip.setLayoutParams(lp);
            out.addView(ip);
        }
        buildModeUi();
    }

    EditText ipField() {
        View v = out.findViewWithTag("ip");
        return v instanceof EditText ? (EditText) v : null;
    }

    void buildModeUi() {
        if (tool.equals("LAN Chat") || tool.equals("Phone Control (LAN)") || tool.equals("Find My Phone")) {
            if (tool.equals("LAN Chat")) {
                final EditText msg = Ui.input(this, "Type a message…", false);
                msg.setTag("msg");
                out.addView(msg);
                Button send = Ui.button(this, "➤ SEND", Ui.GREEN);
                send.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        EditText m = (EditText) out.findViewWithTag("msg");
                        String t = m.getText().toString().trim();
                        if (t.length() == 0 || !send("CHAT:" + t)) {
                            toast("Connect first");
                            return;
                        }
                        m.setText("");
                        appendLog("ME: " + t);
                    }
                });
                out.addView(send);
            }
            if (tool.equals("Phone Control (LAN)")) {
                final EditText msg = Ui.input(this, "Message to show on the other phone…", false);
                msg.setTag("pcmsg");
                out.addView(msg);
                LinearLayout r1 = Ui.row(this);
                r1.addView(wbtn(cmdBtn("🔔 RING", "FIND")));
                r1.addView(wbtn(cmdBtn("📳 VIBRATE", "VIB")));
                out.addView(r1);
                Button ms = Ui.button(this, "💬 SHOW MESSAGE", Ui.CYAN);
                ms.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        EditText m = (EditText) out.findViewWithTag("pcmsg");
                        if (!send("MSG:" + m.getText().toString().trim())) toast("Connect first");
                    }
                });
                out.addView(ms);
                Button bt = Ui.button(this, "🔋 ASK BATTERY", Ui.GOLD);
                bt.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        send("BATT?");
                    }
                });
                out.addView(bt);
            }
            if (tool.equals("Find My Phone")) {
                Button ring = Ui.button(this, "🚨 RING THE PHONE", Ui.MAGENTA);
                ring.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!send("FIND")) toast("Connect first");
                    }
                });
                out.addView(ring);
                Button stop = Ui.button(this, "⏹ STOP RINGING", Ui.GOLD);
                stop.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        send("STOP");
                    }
                });
                out.addView(stop);
            }
        } else if (tool.equals("Walkie-Talkie")) {
            Button talk = Ui.button(this, "🎙 TALK (PRESS & HOLD)", Ui.GREEN);
            talk.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    if (e.getAction() == MotionEvent.ACTION_DOWN) {
                        talking = true;
                        appendLog("tx…");
                        return true;
                    } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                        talking = false;
                        return true;
                    }
                    return false;
                }
            });
            out.addView(talk);
            out.addView(Ui.text(this, "Hold to transmit. Voice plays on the other phone instantly.", 11, Ui.DIM, false));
        } else if (tool.equals("WiFi Call")) {
            LinearLayout r = Ui.row(this);
            Button mute = Ui.button(this, "🔇 MUTE", Ui.GOLD);
            mute.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    muted = !muted;
                    toast(muted ? "Mic muted" : "Mic live");
                }
            });
            r.addView(wbtn(mute));
            Button end = Ui.button(this, "⏹ END", Ui.MAGENTA);
            end.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            ep.leftMargin = Ui.dp(this, 8);
            end.setLayoutParams(ep);
            r.addView(end);
            out.addView(r);
            out.addView(Ui.text(this, "Voice streams both directions live. MUTE silences your mic.", 11, Ui.DIM, false));
        } else if (tool.equals("LAN Clipboard Sync")) {
            Button send = Ui.button(this, "📋 PUSH MY CLIPBOARD", Ui.CYAN);
            send.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String t = "";
                    try {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                                getSystemService(CLIPBOARD_SERVICE);
                        if (cm.hasPrimaryClip() && cm.getPrimaryClip() != null) {
                            CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(WifiCommActivity.this);
                            t = cs == null ? "" : cs.toString();
                        }
                    } catch (Exception ignored) {
                    }
                    if (t.length() == 0) {
                        toast("Clipboard is empty");
                        return;
                    }
                    if (!send("CLIP:" + t)) toast("Connect first");
                }
            });
            out.addView(send);
        } else if (tool.equals("Battery Remote")) {
            Button ask = Ui.button(this, "🔋 ASK REMOTE BATTERY", Ui.GREEN);
            ask.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!send("BATT?")) toast("Connect first");
                }
            });
            out.addView(ask);
        }
    }

    Button wbtn(Button b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        b.setLayoutParams(lp);
        return b;
    }

    Button cmdBtn(String label, final String cmd) {
        Button b = Ui.button(this, label, Ui.GREEN);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!send(cmd)) toast("Connect first");
            }
        });
        return b;
    }

    // ---------------------------------------------------------------- link

    void startHost() {
        if (connected) {
            toast("Already hosting");
            return;
        }
        appendLog("HOST: listening on " + LanComm.localIp() + ":" + LanComm.PORT + "…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ss = new ServerSocket(LanComm.PORT);
                    final Socket s = ss.accept();
                    sock = s;
                    wire(s);
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            appendLog("HOST error: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    void startClient() {
        if (connected) {
            toast("Already connected");
            return;
        }
        final EditText ipf = ipField();
        final String ip = ipf == null ? "" : ipf.getText().toString().trim();
        if (ip.length() == 0) {
            toast("Enter the host phone's IP first");
            return;
        }
        appendLog("CLIENT: connecting to " + ip + "…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket s = new Socket();
                    s.connect(new InetSocketAddress(ip, LanComm.PORT), 4000);
                    sock = s;
                    wire(s);
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            appendLog("CLIENT error: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    void wire(Socket s) throws Exception {
        outStream = s.getOutputStream();
        inReader = new BufferedReader(new InputStreamReader(s.getInputStream()));
        connected = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appendLog("✓ LINKED");
                Ui.flash(logCard);
                if (voiceMode) startVoice();
            }
        });
        if (voiceMode) {
            voiceReadLoop();
        } else {
            lineLoop();
        }
    }

    void lineLoop() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String line;
                    while (running && (line = inReader.readLine()) != null) {
                        handleLine(line);
                    }
                } catch (Exception ignored) {
                }
                connected = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("Link closed");
                    }
                });
            }
        }).start();
    }

    void handleLine(final String line) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (line.startsWith("CHAT:")) {
                    appendLog("REMOTE: " + line.substring(5));
                } else if (line.startsWith("CLIP:")) {
                    try {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                                getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("LAN Sync", line.substring(5)));
                        appendLog("✓ Clipboard received & copied");
                        toast("Clipboard synced from other phone");
                    } catch (Exception ignored) {
                    }
                } else if (line.equals("BATT?")) {
                    send(LanComm.batteryLine(WifiCommActivity.this));
                } else if (line.startsWith("BATT:")) {
                    String[] p = line.split(":");
                    appendLog("REMOTE BATTERY: " + p[1] + "% · " + (p.length > 2 && p[2].equals("1") ? "charging" : "on battery"));
                } else if (line.equals("FIND")) {
                    startRemoteAlarm();
                } else if (line.equals("STOP")) {
                    stopRemoteAlarm();
                } else if (line.startsWith("MSG:")) {
                    appendLog("REMOTE MESSAGE: " + line.substring(4));
                    try {
                        new android.app.AlertDialog.Builder(WifiCommActivity.this)
                                .setTitle("📲 Remote message")
                                .setMessage(line.substring(4))
                                .setPositiveButton("OK", null)
                                .show();
                    } catch (Exception ignored) {
                    }
                } else if (line.equals("VIB")) {
                    vibrate(600);
                    appendLog("Remote asked me to vibrate");
                }
            }
        });
    }

    boolean send(String s) {
        if (!connected || outStream == null) return false;
        final String msg = s;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (this) {
                        outStream.write((msg + "\n").getBytes("UTF-8"));
                        outStream.flush();
                    }
                } catch (Exception ignored) {
                }
            }
        }).start();
        return true;
    }

    // ---------------------------------------------------------------- voice

    void startVoice() {
        try {
            int rate = 16000;
            int minOut = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack(AudioManager.STREAM_MUSIC, rate,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(4096, minOut), AudioTrack.MODE_STREAM);
            track.play();
            int minIn = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            rec = new AudioRecord(MediaRecorder.AudioSource.MIC, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(4096, minIn));
            rec.startRecording();
            voiceReadLoop();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] buf = new byte[2048];
                    while (running && connected && rec != null) {
                        int n = rec.read(buf, 0, buf.length);
                        boolean talkNow = tool.equals("WiFi Call") ? (!muted) : talking;
                        if (n > 0 && talkNow) {
                            try {
                                outStream.write(buf, 0, n);
                                outStream.flush();
                            } catch (Exception e) {
                                break;
                            }
                        }
                    }
                }
            }).start();
            appendLog(voiceMode ? "CALL LIVE — speak now" : "Radio ready — hold TALK");
        } catch (Exception e) {
            appendLog("Voice init failed: " + e.getMessage());
        }
    }

    void voiceReadLoop() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buf = new byte[2048];
                try {
                    java.io.InputStream in = sock.getInputStream();
                    while (running && connected) {
                        int n = in.read(buf);
                        if (n < 0) break;
                        if (track != null && n > 0) track.write(buf, 0, n);
                    }
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    // ---------------------------------------------------------------- alarm (find my phone / remote ring)

    void startRemoteAlarm() {
        if (alarmOn) return;
        alarmOn = true;
        appendLog("🚨 REMOTE RING STARTED — tap STOP or send STOP from the other phone");
        vibrateLoop();
        sirenT = new Thread(new Runnable() {
            @Override
            public void run() {
                int rate = 44100;
                AudioTrack at = null;
                try {
                    int min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    at = new AudioTrack(AudioManager.STREAM_MUSIC, rate,
                            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                            Math.max(8192, min), AudioTrack.MODE_STREAM);
                    at.play();
                    short[] buf = new short[2048];
                    double phase = 0;
                    boolean hi = true;
                    long lastSwap = 0;
                    while (alarmOn && running) {
                        double f = hi ? 1100 : 750;
                        for (int i = 0; i < buf.length; i++) {
                            buf[i] = (short) (Math.sin(phase) * 12000);
                            phase += 2 * Math.PI * f / rate;
                            if (phase > 2 * Math.PI) phase -= 2 * Math.PI;
                        }
                        at.write(buf, 0, buf.length);
                        long now = System.currentTimeMillis();
                        if (now - lastSwap > 350) {
                            hi = !hi;
                            lastSwap = now;
                        }
                    }
                    at.stop();
                    at.release();
                } catch (Exception ignored) {
                    if (at != null) {
                        try {
                            at.stop();
                            at.release();
                        } catch (Exception ignored2) {
                        }
                    }
                }
            }
        });
        sirenT.start();
        torchBlink();
    }

    void stopRemoteAlarm() {
        alarmOn = false;
        appendLog("Ring stopped");
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            v.cancel();
        } catch (Exception ignored) {
        }
        torch(false);
    }

    void vibrateLoop() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            long[] pattern = {0, 500, 300};
            v.vibrate(VibrationEffect.createWaveform(pattern, 0));
        } catch (Exception ignored) {
        }
    }

    void vibrate(int ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {
        }
    }

    void torchBlink() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (alarmOn && running) {
                    torch(true);
                    try {
                        Thread.sleep(250);
                    } catch (Exception ignored) {
                    }
                    torch(false);
                    try {
                        Thread.sleep(250);
                    } catch (Exception ignored) {
                    }
                }
                torch(false);
            }
        }).start();
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

    // ---------------------------------------------------------------- misc

    void appendLog(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                log.setText(log.getText().toString() + "\n" + s);
            }
        });
    }

    void toast(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(WifiCommActivity.this, s, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        running = false;
        alarmOn = false;
        connected = false;
        talking = false;
        try {
            if (sock != null) sock.close();
        } catch (Exception ignored) {
        }
        try {
            if (ss != null) ss.close();
        } catch (Exception ignored) {
        }
        try {
            if (rec != null) rec.stop();
        } catch (Exception ignored) {
        }
        try {
            if (rec != null) rec.release();
        } catch (Exception ignored) {
        }
        try {
            if (track != null) {
                track.stop();
                track.release();
            }
        } catch (Exception ignored) {
        }
    }
}
