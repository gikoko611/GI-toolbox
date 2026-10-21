package com.gikoko.gitoolbox;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
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
import java.util.ArrayList;
import java.util.List;

/** Native video tools v3.0: frame extractor, video→GIF, mute, cutter, rotate, merger. */
public class VideoToolActivity extends Activity {

    private String tool;
    private LinearLayout box;
    private TextView status;
    private Uri pickedVideo;
    private Uri pickedVideoB;
    private File pendingOut;
    private String pendingMime = "video/mp4";
    private String pendingName = "gitoolbox.mp4";
    private byte[] pendingGif;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tool = getIntent() == null ? null : getIntent().getStringExtra("tool");
        if (tool == null) tool = "Frame Extractor";
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
        status = Ui.text(this, "", 13, Ui.TEXT, false);
        build();
    }

    private void build() {
        if (tool.equals("Frame Extractor")) buildFrames();
        else if (tool.equals("Video → GIF")) buildVideoGif();
        else if (tool.equals("Video Mute")) buildMute();
        else if (tool.equals("Video Cutter")) buildCutter();
        else if (tool.equals("Video Rotate")) buildRotate();
        else if (tool.equals("Video Merger")) buildMerger();
        else buildPanel();
    }

    private void buildPanel() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, tool, 16, Ui.GOLD, true));
        String msg;
        if (tool.equals("Video Compressor")) {
            msg = "Re-encoding video at lower bitrate needs a hardware encoder module, which ships with the next APK update.\n\nTip: for smaller files today, use Video Cutter to trim unwanted parts.";
        } else if (tool.equals("Video Crop")) {
            msg = "Pixel-level cropping needs the re-encode module, which ships with the next APK update.";
        } else if (tool.equals("Video Speed")) {
            msg = "Speed change without re-encoding is not supported by Android's muxer. This ships with the next APK update.";
        } else if (tool.equals("Video Reverse")) {
            msg = "Reversing a video needs full re-encoding, which ships with the next APK update.";
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
                Toast.makeText(VideoToolActivity.this, s, Toast.LENGTH_SHORT).show();
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

    private int intOf(EditText e, int def) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (Exception ex) {
            return def;
        }
    }

    private Button pickVideo(final int code, String label) {
        Button b = Ui.button(this, label, Ui.GOLD);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("video/*");
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

    void savePending(final String mime, final String name) {
        pendingMime = mime;
        pendingName = name;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType(mime);
                it.putExtra("android.intent.extra.TITLE", name);
                startActivityForResult(it, 2);
            }
        });
    }

    // ------------------------------------------------------------ frame extractor

    private void buildFrames() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Grabs frames from your video and saves them as PNG images in Pictures/GIToolbox.", 13, Ui.DIM, false));
        c.addView(pickVideo(20, "Pick Video"));
        c.addView(spacer());
        final EditText n = Ui.input(this, "How many frames (default 6, max 12)", false);
        n.setText("6");
        c.addView(n);
        status.setText("");
        c.addView(status);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Extract Frames");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedVideo == null) { toast("Pick a video first"); return; }
                final int count = Math.max(1, Math.min(12, intOf(n, 6)));
                toast("Extracting…");
                new Thread() {
                    @Override
                    public void run() {
                        MediaMetadataRetriever r = new MediaMetadataRetriever();
                        try {
                            File in = copyToCache(pickedVideo, "vid_in.bin");
                            r.setDataSource(in.getAbsolutePath());
                            long durMs = Long.parseLong(r.extractMetadata(
                                    MediaMetadataRetriever.METADATA_KEY_DURATION));
                            int saved = 0;
                            for (int i = 0; i < count; i++) {
                                long at = durMs * (i + 1) / (count + 1) * 1000L; // µs
                                Bitmap b = r.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                                if (b == null) continue;
                                saveGallery("GIToolbox_frame_" + (i + 1) + ".png", b);
                                b.recycle();
                                saved++;
                            }
                            final int ok = saved;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    setStatus(ok > 0 ? "Saved " + ok + " frames to Pictures/GIToolbox ✓" : "Could not decode that video");
                                    toast(ok > 0 ? "Saved " + ok + " frames ✓" : "Failed");
                                }
                            });
                        } catch (final Exception e) {
                            toast("Failed: " + e.getMessage());
                        } finally {
                            try {
                                r.release();
                            } catch (Exception ignore) {
                            }
                        }
                    }
                }.start();
            }
        });
        box.addView(go);
    }

    private void saveGallery(String name, Bitmap b) {
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name);
            v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GIToolbox");
            }
            Uri u = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (u == null) return;
            OutputStream os = getContentResolver().openOutputStream(u);
            b.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.close();
        } catch (Exception ignore) {
        }
    }

    // ------------------------------------------------------------ video -> gif

    private void buildVideoGif() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Turns a video clip into an animated GIF (frames + fixed palette encoder).", 13, Ui.DIM, false));
        c.addView(pickVideo(20, "Pick Video"));
        c.addView(spacer());
        final EditText n = Ui.input(this, "Frames (default 10, max 20)", false);
        n.setText("10");
        final EditText w = Ui.input(this, "GIF width px (default 320)", false);
        w.setText("320");
        c.addView(n);
        c.addView(w);
        status.setText("");
        c.addView(status);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Create GIF");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedVideo == null) { toast("Pick a video first"); return; }
                final int count = Math.max(2, Math.min(20, intOf(n, 10)));
                final int width = Math.max(120, Math.min(640, intOf(w, 320)));
                toast("Encoding GIF… (takes a few seconds)");
                new Thread() {
                    @Override
                    public void run() {
                        MediaMetadataRetriever r = new MediaMetadataRetriever();
                        try {
                            File in = copyToCache(pickedVideo, "vid_in.bin");
                            r.setDataSource(in.getAbsolutePath());
                            long durMs = 1;
                            try {
                                durMs = Long.parseLong(r.extractMetadata(
                                        MediaMetadataRetriever.METADATA_KEY_DURATION));
                            } catch (Exception ignore) {
                            }
                            List<Bitmap> frames = new ArrayList<Bitmap>();
                            for (int i = 0; i < count; i++) {
                                long at = durMs * i / count * 1000L;
                                Bitmap b = r.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                                if (b == null) continue;
                                float f = width / (float) b.getWidth();
                                int hh = Math.max(1, (int) (b.getHeight() * f));
                                frames.add(Bitmap.createScaledBitmap(b, width, hh, true));
                            }
                            if (frames.size() < 2) throw new Exception("Could not read frames from that video");
                            int delayCs = (int) Math.max(2, (durMs / frames.size()) / 10);
                            if (delayCs > 60) delayCs = 60;
                            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                            GifEncoder.encode(frames, delayCs, bos);
                            pendingGif = bos.toByteArray();
                            savePending("image/gif", "gitoolbox.gif");
                        } catch (final Exception e) {
                            toast("GIF failed: " + e.getMessage());
                        } finally {
                            try {
                                r.release();
                            } catch (Exception ignore) {
                            }
                        }
                    }
                }.start();
            }
        });
        box.addView(go);
    }

    // ------------------------------------------------------------ mute

    private void buildMute() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Removes the audio track — video quality stays 100% (no re-encode).", 13, Ui.DIM, false));
        c.addView(pickVideo(20, "Pick Video"));
        status.setText("");
        c.addView(status);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Remove Audio");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedVideo == null) { toast("Pick a video first"); return; }
                toast("Processing…");
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            File in = copyToCache(pickedVideo, "vid_in.bin");
                            File out = new File(getCacheDir(), "vid_out.mp4");
                            if (out.exists()) out.delete();
                            long n = AudioVideoMux.mux(in, out, AudioVideoMux.MODE_VIDEO, 0, Long.MAX_VALUE, null);
                            if (n <= 0) throw new Exception("No video track found");
                            pendingOut = out;
                            savePending("video/mp4", "gitoolbox_muted.mp4");
                        } catch (final Exception e) {
                            toast("Failed: " + e.getMessage());
                        }
                    }
                }.start();
            }
        });
        box.addView(go);
    }

    // ------------------------------------------------------------ cutter

    private void buildCutter() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Cut a clip out of a video (start → end seconds). No re-encode — fast and lossless.", 13, Ui.DIM, false));
        c.addView(pickVideo(20, "Pick Video"));
        c.addView(spacer());
        final EditText st = Ui.input(this, "Start second (e.g. 5)", false);
        st.setText("0");
        final EditText en = Ui.input(this, "End second (blank = to the end)", false);
        c.addView(st);
        c.addView(en);
        status.setText("");
        c.addView(status);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "✂ Cut Video");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedVideo == null) { toast("Pick a video first"); return; }
                final float s;
                final float e;
                try {
                    s = Float.parseFloat(st.getText().toString().trim());
                } catch (Exception ex) {
                    toast("Enter the start second");
                    return;
                }
                try {
                    e = Float.parseFloat(en.getText().toString().trim());
                } catch (Exception ex) {
                    toast("Enter the end second");
                    return;
                }
                if (e <= s) { toast("End must be after start"); return; }
                toast("Cutting…");
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            File in = copyToCache(pickedVideo, "vid_in.bin");
                            File out = new File(getCacheDir(), "vid_out.mp4");
                            if (out.exists()) out.delete();
                            long n = AudioVideoMux.mux(in, out, AudioVideoMux.MODE_ALL,
                                    (long) (s * 1000000f), (long) (e * 1000000f), null);
                            if (n <= 0) throw new Exception("Nothing written — check the range");
                            pendingOut = out;
                            savePending("video/mp4", "gitoolbox_clip.mp4");
                        } catch (final Exception ex) {
                            toast("Failed: " + ex.getMessage());
                        }
                    }
                }.start();
            }
        });
        box.addView(go);
    }

    // ------------------------------------------------------------ rotate

    private void buildRotate() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Rotates the video by writing rotation metadata — instant, lossless, plays everywhere.", 13, Ui.DIM, false));
        c.addView(pickVideo(20, "Pick Video"));
        c.addView(spacer());
        final EditText d = Ui.input(this, "Degrees: 90 / 180 / 270 (default 90)", false);
        d.setText("90");
        status.setText("");
        c.addView(status);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Rotate Video");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedVideo == null) { toast("Pick a video first"); return; }
                final int deg = intOf(d, 90);
                if (deg != 90 && deg != 180 && deg != 270) { toast("Use 90, 180 or 270"); return; }
                toast("Rotating…");
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            File in = copyToCache(pickedVideo, "vid_in.bin");
                            File out = new File(getCacheDir(), "vid_out.mp4");
                            if (out.exists()) out.delete();
                            long n = AudioVideoMux.mux(in, out, AudioVideoMux.MODE_ALL, 0, Long.MAX_VALUE,
                                    Integer.valueOf(deg));
                            if (n <= 0) throw new Exception("No tracks found");
                            pendingOut = out;
                            savePending("video/mp4", "gitoolbox_rotated.mp4");
                        } catch (final Exception e) {
                            toast("Failed: " + e.getMessage());
                        }
                    }
                }.start();
            }
        });
        box.addView(go);
    }

    // ------------------------------------------------------------ merger

    private void buildMerger() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Joins two videos into one file. Both must be the same format and resolution (audio is dropped).", 13, Ui.DIM, false));
        c.addView(pickVideo(20, "Pick Video A"));
        c.addView(spacer());
        c.addView(pickVideo(21, "Pick Video B"));
        status.setText("");
        c.addView(status);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Merge A + B");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedVideo == null || pickedVideoB == null) { toast("Pick both videos first"); return; }
                toast("Merging…");
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            File a = copyToCache(pickedVideo, "vid_a.bin");
                            File b = copyToCache(pickedVideoB, "vid_b.bin");
                            File out = new File(getCacheDir(), "vid_out.mp4");
                            if (out.exists()) out.delete();
                            AudioVideoMux.merge(a, b, out, false);
                            pendingOut = out;
                            savePending("video/mp4", "gitoolbox_merged.mp4");
                        } catch (final Exception e) {
                            toast("Merge failed: " + e.getMessage());
                        }
                    }
                }.start();
            }
        });
        box.addView(go);
    }

    // ------------------------------------------------------------ result

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        Uri u = data.getData();
        if (req == 2) {
            try {
                OutputStream os = getContentResolver().openOutputStream(u);
                if (pendingGif != null) {
                    os.write(pendingGif);
                    pendingGif = null;
                    os.close();
                    toast("GIF saved ✓");
                    return;
                }
                if (pendingOut != null && pendingOut.exists()) {
                    FileInputStream in = new FileInputStream(pendingOut);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    in.close();
                    os.close();
                    toast("Saved ✓");
                }
            } catch (Exception e) {
                toast("Save failed: " + e.getMessage());
            }
        } else if (req == 20) {
            pickedVideo = u;
            toast("Video A loaded ✓");
        } else if (req == 21) {
            pickedVideoB = u;
            toast("Video B loaded ✓");
        }
    }
}
