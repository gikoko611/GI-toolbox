package com.gikoko.gitoolbox;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Flashlight / Vibration / Mirror Camera hardware tools. */
public class HardwareActivity extends Activity {

    private String tool;
    private boolean torchOn;
    private CameraManager cm;
    private String camId;
    private TextView status;
    private LinearLayout rootLayout;
    private static final int PIC_REQ = 31;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tool = getIntent().getStringExtra("tool");
        cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        LinearLayout root = Ui.screen(this);
        rootLayout = root;
        root.addView(Ui.header(this, tool, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));
        status = Ui.text(this, "", 13, Ui.TEXT, false);
        LinearLayout card = Ui.card(this);
        card.addView(status);
        root.addView(card);

        if (tool.equals("Flashlight") || tool.equals("Flashlight Torch")) {
            addTorch(root);
        } else if (tool.equals("Vibration Test")) {
            addVibrate(root);
        } else if (tool.equals("Mirror Camera")) {
            addMirror(root);
        }
        setContentView(Ui.scroll(this, root));
    }

    void addTorch(LinearLayout root) {
        status.setText("Controls the camera flash as a torch.\n(Works on Redmi 9A and most devices with a camera flash.)");
        final Button btn = Ui.button(this, "🔆 TURN ON");
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (camId == null) {
                        for (String id : cm.getCameraIdList()) {
                            CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                            Boolean has = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                            if (has != null && has) {
                                camId = id;
                                break;
                            }
                        }
                    }
                    if (camId == null) {
                        Toast.makeText(HardwareActivity.this, "No camera flash found", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!torchOn) {
                        cm.setTorchMode(camId, true);
                        torchOn = true;
                        btn.setText("🌙 TURN OFF");
                    } else {
                        cm.setTorchMode(camId, false);
                        torchOn = false;
                        btn.setText("🔆 TURN ON");
                    }
                } catch (Exception e) {
                    Toast.makeText(HardwareActivity.this, "Torch error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(btn);
    }

    void addVibrate(LinearLayout root) {
        status.setText("Tests your phone's vibration motor.");
        Button b1 = Ui.button(this, "📳 SHORT VIBRATION");
        b1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vibrate(300);
            }
        });
        Button b2 = Ui.button(this, "📳 LONG VIBRATION");
        b2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vibrate(1500);
            }
        });
        Button b3 = Ui.button(this, "📳 PATTERN: SOS");
        b3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vibrate(0);
            }
        });
        root.addView(b1);
        root.addView(b2);
        root.addView(b3);
    }

    void vibrate(long ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) {
                Toast.makeText(this, "No vibrator on this device", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ms == 0) {
                long[] pattern = {0, 100, 80, 100, 80, 100, 250, 300, 250, 300, 250, 100, 80, 100, 80, 100};
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    v.vibrate(pattern, -1);
                }
            } else {
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(ms);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Vibrate failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    void addMirror(LinearLayout root) {
        status.setText("Take a selfie with the front camera and see it mirrored, like a real mirror.\n1. Tap TAKE SELFIE\n2. Allow camera permission if asked");
        Button b = Ui.button(this, "📷 TAKE SELFIE");
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                if (it.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(it, PIC_REQ);
                } else {
                    Toast.makeText(HardwareActivity.this, "No camera app available", Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(b);
        Button save = Ui.button(this, "💾 SAVE MIRRORED PHOTO", Ui.GOLD);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveMirror();
            }
        });
        root.addView(save);
    }

    private Bitmap mirror;

    void saveMirror() {
        if (mirror == null) {
            Toast.makeText(this, "Take a selfie first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String name = "GIToolbox_mirror_" + System.currentTimeMillis() + ".png";
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name);
            cv.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= 29) {
                cv.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GIToolbox");
            }
            android.net.Uri uri = getContentResolver().insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            java.io.OutputStream os = getContentResolver().openOutputStream(uri);
            mirror.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.close();
            Toast.makeText(this, "Saved to Pictures/GIToolbox", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PIC_REQ && res == RESULT_OK && data != null && data.getExtras() != null) {
            Bitmap bmp = (Bitmap) data.getExtras().get("data");
            if (bmp == null) return;
            Matrix m = new Matrix();
            m.preScale(-1, 1);
            mirror = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, false);
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(mirror);
            iv.setAdjustViewBounds(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            iv.setLayoutParams(lp);
            iv.setPadding(0, Ui.dp(this, 12), 0, 0);
            rootLayout.addView(iv);
            Toast.makeText(this, "Mirrored! Tap SAVE to keep it", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (torchOn && camId != null) {
            try {
                cm.setTorchMode(camId, false);
                torchOn = false;
            } catch (Exception ignored) {
            }
        }
    }
}
