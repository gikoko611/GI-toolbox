package com.gikoko.gitoolbox;

import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * v0.0.1 Camera Lab:
 * - CCTV Camera: motion-detect security cam + tiny HTTP server — watch the live
 *   latest snapshot from any browser on the same Wi-Fi (http://<phone-ip>:8089/).
 * - Stop Motion Studio: capture frames and export a real animated GIF.
 * 100% native, deprecated Camera API (fully functional), no libraries.
 */
public class CameraLabActivity extends Activity implements SurfaceHolder.Callback {

    private String tool;
    private LinearLayout out;
    private boolean cctv;

    private Camera cam;
    private SurfaceView surface;
    private SurfaceHolder holder;
    private volatile boolean camReady = false;
    private boolean capturing = false;

    // cctv
    private ServerSocket httpServer;
    private volatile boolean serving = false;
    private byte[] latestJpeg;
    private int motionCount = 0;
    private long lastShot = 0;
    private int prevAvg = -1;
    private int frameNo = 0;
    private TextView camStatus;

    // stop motion
    private final List<Bitmap> frames = new ArrayList<Bitmap>();
    private TextView frameLabel;
    private volatile int fps = 8;
    private volatile boolean exporting = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tool = getIntent().getStringExtra("tool");
        cctv = tool.equals("CCTV Camera");
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.header(this, tool, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));
        LinearLayout br = Ui.row(this);
        br.addView(Ui.badge(this, cctv ? "● SECURITY · MOTION AI" : "● STUDIO · GIF ENGINE", Ui.CYAN));
        root.addView(br);
        Ui.reveal(br);

        out = new LinearLayout(this);
        out.setOrientation(LinearLayout.VERTICAL);
        root.addView(out);

        if (checkSelfPermission(android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 12);
        }

        intro();
        buildPreview();
        controls();
        setContentView(Ui.scroll(this, root));
    }

    void intro() {
        String s;
        if (cctv) {
            s = "Turn a spare phone into a security camera. GIToolbox watches the live feed, "
                    + "detects motion, saves every trigger shot to Pictures/GIToolbox and serves "
                    + "the latest image to any browser on your Wi-Fi.";
        } else {
            s = "Frame-by-frame stop motion studio: point the camera, CAPTURE each pose, "
                    + "then EXPORT a real animated GIF to your gallery. Great for claymation, "
                    + "toys and drawing flipbooks.";
        }
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, s, 12, Ui.TEXT, false));
        out.addView(c);
        Ui.reveal(c);
    }

    void buildPreview() {
        LinearLayout pc = Ui.card(this);
        pc.addView(Ui.text(this, "LIVE PREVIEW", 10, Ui.DIM, true));
        surface = new SurfaceView(this);
        holder = surface.getHolder();
        holder.addCallback(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 320));
        lp.topMargin = Ui.dp(this, 6);
        surface.setLayoutParams(lp);
        pc.addView(surface);
        out.addView(pc);

        camStatus = Ui.text(this, cctv ? "Camera starting…" : "Camera starting…", 12, Ui.GOLD, true);
        camStatus.setPadding(0, Ui.dp(this, 8), 0, 0);
        pc.addView(camStatus);
    }

    void controls() {
        if (cctv) {
            LinearLayout c = Ui.card(this);
            String url = "http://" + LanComm.localIp() + ":8089/";
            c.addView(Ui.text(this, "WATCH FROM ANY DEVICE ON THIS WI-FI:", 10, Ui.DIM, true));
            TextView u = Ui.text(this, url, 15, Ui.CYAN, true);
            u.setGravity(Gravity.CENTER);
            u.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            c.addView(u);
            c.addView(Ui.text(this, "Open this address in a browser on the same Wi-Fi. "
                    + "Refresh to get the newest motion-triggered snapshot. Keep this screen on "
                    + "(Settings → Display → Keep screen on) while monitoring.", 10, Ui.DIM, false));
            out.addView(c);

            Button start = Ui.button(this, "🛰 START CCTV SERVER", Ui.GREEN);
            start.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startHttpServer();
                    toast("Server live on port 8089");
                }
            });
            out.addView(pad(start));
        } else {
            LinearLayout c = Ui.card(this);
            frameLabel = Ui.text(this, "0 frames captured", 14, Ui.CYAN, true);
            c.addView(frameLabel);
            out.addView(c);

            Button cap = Ui.button(this, "📸 CAPTURE FRAME", Ui.GREEN);
            cap.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    captureFrame();
                }
            });
            out.addView(pad(cap));

            c = Ui.card(this);
            TextView fl = Ui.text(this, "PLAYBACK SPEED — " + fps + " fps", 12, Ui.GOLD, true);
            fl.setTag("fpslabel");
            c.addView(fl);
            SeekBar sb = new SeekBar(this);
            sb.setMax(18); // 2..20
            sb.setProgress(6);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int p, boolean u) {
                    fps = p + 2;
                    updateFrameLabel();
                }

                @Override
                public void onStartTrackingTouch(SeekBar s) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar s) {
                }
            });
            c.addView(sb);
            out.addView(c);

            LinearLayout r = Ui.row(this);
            Button exp = Ui.button(this, "🎬 EXPORT GIF", Ui.MAGENTA);
            exp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    exportGif();
                }
            });
            LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            exp.setLayoutParams(el);
            r.addView(exp);
            Button clear = Ui.button(this, "🗑 CLEAR", Ui.RED);
            clear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    frames.clear();
                    updateFrameLabel();
                    toast("Frames cleared");
                }
            });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            cl.leftMargin = Ui.dp(this, 8);
            clear.setLayoutParams(cl);
            r.addView(clear);
            out.addView(pad(r));
        }
    }

    // ------------------------------------------------------------ camera lifecycle

    void openCam() {
        try {
            cam = Camera.open(0);
            try {
                Camera.Parameters p = cam.getParameters();
                if (p.getSupportedPreviewSizes() != null && p.getSupportedPreviewSizes().size() > 0) {
                    Camera.Size best = p.getSupportedPreviewSizes().get(0);
                    for (Camera.Size s : p.getSupportedPreviewSizes()) {
                        if (s.width >= 320 && s.width <= 640
                                && (best.width > 640 || best.width < 320 || s.width > best.width)) {
                            best = s;
                        }
                    }
                    p.setPreviewSize(best.width, best.height);
                    cam.setParameters(p);
                }
            } catch (Exception ignored) {
            }
            cam.setDisplayOrientation(90);
            cam.setPreviewDisplay(holder);
            cam.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    onFrame(data);
                }
            });
            cam.startPreview();
            camReady = true;
            setStatus("● CAMERA LIVE" + (cctv ? " · MOTION DETECTION ON" : " · READY TO CAPTURE"), Ui.GREEN);
        } catch (Exception e) {
            setStatus("Camera failed to start: " + e.getClass().getSimpleName(), Ui.RED);
        }
    }

    void releaseCam() {
        camReady = false;
        try {
            if (cam != null) {
                cam.setPreviewCallback(null);
                cam.stopPreview();
                cam.release();
            }
        } catch (Exception ignored) {
        }
        cam = null;
    }

    void onFrame(byte[] data) {
        if (!cctv || !camReady || capturing) return;
        frameNo++;
        if (frameNo % 8 != 0) return;
        // average luminance of sampled Y-plane pixels (NV21)
        int sum = 0, n = 0;
        for (int i = 0; i < data.length; i += 512) {
            sum += data[i] & 0xFF;
            n++;
        }
        if (n == 0) return;
        int avg = sum / n;
        if (prevAvg >= 0 && Math.abs(avg - prevAvg) >= 9) {
            long now = System.currentTimeMillis();
            if (now - lastShot > 4000) {
                lastShot = now;
                motionShot();
            }
        }
        prevAvg = avg;
    }

    void motionShot() {
        if (cam == null || capturing) return;
        capturing = true;
        try {
            cam.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    capturing = false;
                    if (data != null) {
                        latestJpeg = data;
                        motionCount++;
                        saveShot(data);
                        restartPreview();
                    }
                }
            });
        } catch (Exception e) {
            capturing = false;
        }
    }

    void restartPreview() {
        try {
            if (cam != null) {
                cam.startPreview();
            }
        } catch (Exception ignored) {
        }
    }

    void saveShot(final byte[] jpeg) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ContentValues v = new ContentValues();
                    v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                            "GIToolbox_cctv_" + System.currentTimeMillis() + ".jpg");
                    v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    if (Build.VERSION.SDK_INT >= 29) {
                        v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                                "Pictures/GIToolbox");
                    }
                    Uri u = getContentResolver().insert(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                    if (u != null) {
                        OutputStream os = getContentResolver().openOutputStream(u);
                        os.write(jpeg);
                        os.close();
                    }
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    // ------------------------------------------------------------ stop motion

    void captureFrame() {
        if (cam == null || !camReady) {
            toast("Camera not ready yet");
            return;
        }
        if (frames.size() >= 60) {
            toast("Max 60 frames — export first");
            return;
        }
        capturing = true;
        try {
            cam.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    capturing = false;
                    if (data == null) return;
                    try {
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inSampleSize = 2;
                        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                        if (b != null) {
                            if (b.getWidth() > 640) {
                                float sc = 640f / b.getWidth();
                                b = Bitmap.createScaledBitmap(b, 640,
                                        (int) (b.getHeight() * sc), true);
                            }
                            frames.add(b);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateFrameLabel();
                                    toast("Frame " + frames.size() + " captured");
                                }
                            });
                        }
                    } catch (Exception ignored) {
                    }
                    restartPreview();
                }
            });
        } catch (Exception e) {
            capturing = false;
            toast("Capture failed");
        }
    }

    void updateFrameLabel() {
        if (frameLabel != null) {
            frameLabel.setText(frames.size() + " frames · " + fps + " fps");
        }
        View v = out.findViewWithTag("fpslabel");
        if (v instanceof TextView) {
            ((TextView) v).setText("PLAYBACK SPEED — " + fps + " fps");
        }
    }

    void exportGif() {
        if (frames.size() < 2) {
            toast("Capture at least 2 frames first");
            return;
        }
        if (exporting) return;
        exporting = true;
        toast("Encoding GIF…");
        final List<Bitmap> copy = new ArrayList<Bitmap>(frames);
        final int delay = Math.max(2, Math.round(100f / fps));
        new Thread(new Runnable() {
            @Override
            public void run() {
                OutputStream os = null;
                try {
                    ContentValues v = new ContentValues();
                    v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                            "GIToolbox_stopmotion_" + System.currentTimeMillis() + ".gif");
                    v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/gif");
                    if (Build.VERSION.SDK_INT >= 29) {
                        v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                                "Pictures/GIToolbox");
                    }
                    Uri u = getContentResolver().insert(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                    if (u == null) throw new Exception("insert failed");
                    os = getContentResolver().openOutputStream(u);
                    GifEncoder.encode(copy, delay, os);
                    os.close();
                    toast("GIF saved to Pictures/GIToolbox (" + copy.size() + " frames)");
                } catch (Exception e) {
                    toast("Export failed: " + e.getClass().getSimpleName());
                    try {
                        if (os != null) os.close();
                    } catch (Exception ignored) {
                    }
                } finally {
                    exporting = false;
                }
            }
        }).start();
    }

    // ------------------------------------------------------------ http server

    void startHttpServer() {
        if (serving) {
            toast("Server already running");
            return;
        }
        serving = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    httpServer = new ServerSocket(8089);
                    while (serving) {
                        Socket s = httpServer.accept();
                        try {
                            BufferedReader rd = new BufferedReader(
                                    new InputStreamReader(s.getInputStream()));
                            String line = rd.readLine();
                            if (line == null || !line.startsWith("GET")) {
                                s.close();
                                continue;
                            }
                            byte[] img;
                            synchronized (CameraLabActivity.this) {
                                img = latestJpeg;
                            }
                            byte[] body = img;
                            if (body == null) {
                                // placeholder gray JPEG message until first motion shot
                                Bitmap b = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
                                b.eraseColor(0xFF0A1626);
                                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                                b.compress(Bitmap.CompressFormat.JPEG, 80, bos);
                                body = bos.toByteArray();
                            }
                            String head = "HTTP/1.1 200 OK\r\n"
                                    + "Content-Type: image/jpeg\r\n"
                                    + "Content-Length: " + body.length + "\r\n"
                                    + "Cache-Control: no-store\r\n"
                                    + "Connection: close\r\n\r\n";
                            OutputStream os = s.getOutputStream();
                            os.write(head.getBytes("UTF-8"));
                            os.write(body);
                            os.flush();
                            s.close();
                        } catch (Exception ignored) {
                            try {
                                s.close();
                            } catch (Exception ignored2) {
                            }
                        }
                    }
                } catch (Exception e) {
                    if (serving) toast("Server error: port 8089 busy?");
                    serving = false;
                }
            }
        }).start();
    }

    // ------------------------------------------------------------ misc

    void setStatus(final String s, final int color) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (camStatus != null) {
                    camStatus.setText(s);
                    camStatus.setTextColor(color);
                }
            }
        });
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
                Toast.makeText(CameraLabActivity.this, s, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == 12 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            toast("Camera permission granted");
            if (cam == null && holder != null && holder.getSurface() != null) {
                openCam();
            }
        } else {
            toast("Camera permission needed — reopen the tool and allow it");
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        openCam();
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int format, int w, int hh) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        releaseCam();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        serving = false;
        camReady = false;
        releaseCam();
        try {
            if (httpServer != null) httpServer.close();
        } catch (Exception ignored) {
        }
        for (Bitmap b : frames) {
            try {
                b.recycle();
            } catch (Exception ignored) {
            }
        }
        frames.clear();
    }
}
