package com.gikoko.gitoolbox;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native image tools: compress, resize, convert, crop, rotate, flip, filters, meme, palette... */
public class ImageToolActivity extends Activity {

    private String tool;
    private Bitmap picked;
    private Bitmap pickedA;
    private Bitmap pickedB;
    private Bitmap pendingResult;
    private String pendingText;
    private Bitmap.CompressFormat pendingFormat = Bitmap.CompressFormat.PNG;
    private String pendingMime = "image/png";
    private String pendingName = "gitoolbox.png";
    private int pendingQuality = 100;

    private LinearLayout box;
    private LinearLayout pickedCard;
    private LinearLayout resultCard;
    private TextView cpHex;
    private int cpValue = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tool = getIntent() == null ? null : getIntent().getStringExtra("tool");
        if (tool == null) tool = "Image Tools";
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.header(this, tool, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));
        ScrollView sc = new ScrollView(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        sc.addView(box);
        root.addView(sc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        pickedCard = Ui.card(this);
        resultCard = Ui.card(this);
        build();
    }

    // ---------------------------------------------------------------- build

    private void build() {
        if (tool.equals("Image Compressor")) buildCompressor();
        else if (tool.equals("Image Resizer")) buildResizer();
        else if (tool.equals("PNG \u2192 JPG")) buildConvert("Convert to JPG", Bitmap.CompressFormat.JPEG, "image/jpeg", "gitoolbox.jpg");
        else if (tool.equals("JPG \u2192 PNG")) buildConvert("Convert to PNG", Bitmap.CompressFormat.PNG, "image/png", "gitoolbox.png");
        else if (tool.equals("WebP Converter")) buildConvert("Convert to WebP", Bitmap.CompressFormat.WEBP, "image/webp", "gitoolbox.webp");
        else if (tool.equals("Image Cropper")) buildCropper();
        else if (tool.equals("Rotate Image")) buildRotate();
        else if (tool.equals("Flip Image")) buildFlip();
        else if (tool.equals("Base64 Encode")) buildB64Enc();
        else if (tool.equals("Base64 Decode")) buildB64Dec();
        else if (tool.equals("Photo Watermark")) buildWatermark();
        else if (tool.equals("Grayscale Filter")) buildFilter("Apply Grayscale", 0);
        else if (tool.equals("Brightness Adjust")) buildBrightContrast(true);
        else if (tool.equals("Contrast Adjust")) buildBrightContrast(false);
        else if (tool.equals("Color Picker")) buildColorPicker();
        else if (tool.equals("Palette Generator")) buildPalette();
        else if (tool.equals("Meme Maker")) buildMeme();
        else if (tool.equals("Image Splitter")) buildSplitter();
        else if (tool.equals("Image Merger")) buildMerger();
        else addPanelNote();
    }

    private void addPanelNote() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, tool, 16, Ui.GOLD, true));
        c.addView(Ui.text(this, "This tool ships with the next APK update.", 14, Ui.TEXT, false));
        box.addView(c);
    }

    private View spacer() {
        View s = new View(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.dp(this, 10)));
        return s;
    }

    private Button pickButton(String label) {
        Button b = Ui.button(this, label, Ui.GOLD);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("image/*");
                startActivityForResult(it, 1);
            }
        });
        return b;
    }

    private Button pickButton(final int code, String label) {
        Button b = Ui.button(this, label, Ui.GOLD);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("image/*");
                startActivityForResult(it, code);
            }
        });
        return b;
    }

    // ---------------------------------------------------------------- pick / save

    private Bitmap safeLoad(Uri u) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream is = getContentResolver().openInputStream(u);
            BitmapFactory.decodeStream(is, null, o);
            is.close();
            int s = 1;
            while (o.outWidth / s > 2048 || o.outHeight / s > 2048) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            is = getContentResolver().openInputStream(u);
            Bitmap b = BitmapFactory.decodeStream(is, null, o2);
            is.close();
            return b;
        } catch (Exception e) {
            toast("Could not load image");
            return null;
        }
    }

    private void pickMain(Uri u) {
        picked = safeLoad(u);
        pickedCard.removeAllViews();
        if (picked == null) {
            pickedCard.addView(Ui.text(this, "Could not load image", 13, 0xFFFF6B6B, false));
        } else {
            pickedCard.addView(Ui.text(this, picked.getWidth() + " x " + picked.getHeight() + " px", 12, Ui.DIM, false));
            pickedCard.addView(preview(picked));
        }
    }

    private ImageView preview(Bitmap b) {
        Bitmap p = b;
        if (b.getWidth() > 900 || b.getHeight() > 900) {
            float f = 900f / Math.max(b.getWidth(), b.getHeight());
            p = Bitmap.createScaledBitmap(b,
                    Math.max(1, (int) (b.getWidth() * f)),
                    Math.max(1, (int) (b.getHeight() * f)), true);
        }
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(p);
        iv.setAdjustViewBounds(true);
        iv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        int m = Ui.dp(this, 6);
        iv.setPadding(m, m, m, m);
        return iv;
    }

    private void showResult(Bitmap b, Bitmap.CompressFormat fmt, int quality, String mime, String name, String msg) {
        pendingResult = b;
        pendingFormat = fmt;
        pendingQuality = quality;
        pendingMime = mime;
        pendingName = name;
        pendingText = null;
        resultCard.removeAllViews();
        resultCard.addView(Ui.text(this, msg, 13, Ui.GREEN, false));
        resultCard.addView(preview(b));
        Button save = Ui.button(this, "Save Image");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType(pendingMime);
                it.putExtra("android.intent.extra.TITLE", pendingName);
                startActivityForResult(it, 2);
            }
        });
        resultCard.addView(save);
        box.addView(resultCard);
    }

    private void showTextResult(String text, String msg) {
        pendingText = text;
        pendingResult = null;
        resultCard.removeAllViews();
        resultCard.addView(Ui.text(this, msg, 13, Ui.GREEN, false));
        TextView tv = Ui.text(this, text, 11, Ui.TEXT, false);
        tv.setTextIsSelectable(true);
        resultCard.addView(tv);
        Button save = Ui.button(this, "Save as .txt");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("text/plain");
                it.putExtra("android.intent.extra.TITLE", "gitoolbox.txt");
                startActivityForResult(it, 2);
            }
        });
        resultCard.addView(save);
        box.addView(resultCard);
    }

    private void saveGallery(String name, Bitmap b) {
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= 29) {
                v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GIToolbox");
            }
            Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (u == null) {
                toast("Save failed");
                return;
            }
            OutputStream os = getContentResolver().openOutputStream(u);
            b.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.close();
        } catch (Exception e) {
            toast("Save failed: " + e.getMessage());
        }
    }

    private Bitmap flatten(Bitmap src) {
        Bitmap flat = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(flat);
        c.drawColor(0xFFFFFFFF);
        c.drawBitmap(src, 0, 0, null);
        return flat;
    }

    private void toast(String s) {
        Toast.makeText(ImageToolActivity.this, s, Toast.LENGTH_SHORT).show();
    }

    private int intOf(EditText e, int def) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (Exception ex) {
            return def;
        }
    }

    // ---------------------------------------------------------------- screens

    private void buildCompressor() {
        box.addView(pickButton("Pick Image"));
        box.addView(spacer());
        box.addView(pickedCard);
        box.addView(spacer());
        LinearLayout c = Ui.card(this);
        final TextView ql = Ui.text(this, "Quality: 85%", 13, Ui.TEXT, false);
        c.addView(ql);
        final SeekBar q = new SeekBar(this);
        q.setMax(90);
        q.setProgress(80);
        q.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ql.setText("Quality: " + (progress + 5) + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        c.addView(q);
        box.addView(c);
        box.addView(spacer());
        Button jpg = Ui.button(this, "Compress to JPG");
        jpg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (picked == null) { toast("Pick an image first"); return; }
                int quality = q.getProgress() + 5;
                Bitmap flat = flatten(picked);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                flat.compress(Bitmap.CompressFormat.JPEG, quality, bos);
                showResult(flat, Bitmap.CompressFormat.JPEG, quality, "image/jpeg", "gitoolbox.jpg",
                        "JPG " + quality + "%  |  size ~" + (bos.size() / 1024) + " KB");
            }
        });
        box.addView(jpg);
        Button webp = Ui.button(this, "Compress to WebP");
        webp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (picked == null) { toast("Pick an image first"); return; }
                int quality = q.getProgress() + 5;
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                picked.compress(Bitmap.CompressFormat.WEBP, quality, bos);
                showResult(picked, Bitmap.CompressFormat.WEBP, quality, "image/webp", "gitoolbox.webp",
                        "WebP " + quality + "%  |  size ~" + (bos.size() / 1024) + " KB");
            }
        });
        box.addView(webp);
    }

    private void buildResizer() {
        box.addView(pickButton("Pick Image"));
        box.addView(spacer());
        box.addView(pickedCard);
        box.addView(spacer());
        LinearLayout c = Ui.card(this);
        final EditText w = Ui.input(this, "Width (px)", false);
        final EditText h = Ui.input(this, "Height (px, blank = keep ratio)", false);
        c.addView(w);
        c.addView(h);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Resize Image");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (picked == null) { toast("Pick an image first"); return; }
                int nw = intOf(w, 0);
                int nh = intOf(h, 0);
                if (nw <= 0 && nh <= 0) { toast("Enter width or height"); return; }
                if (nw <= 0) nw = (int) (picked.getWidth() * (nh / (float) picked.getHeight()));
                if (nh <= 0) nh = (int) (picked.getHeight() * (nw / (float) picked.getWidth()));
                if (nw < 1) nw = 1;
                if (nh < 1) nh = 1;
                Bitmap out = Bitmap.createScaledBitmap(picked, nw, nh, true);
                showResult(out, Bitmap.CompressFormat.PNG, 100, "image/png", "gitoolbox.png",
                        "Resized to " + nw + " x " + nh);
            }
        });
        box.addView(go);
    }

    private void buildConvert(String label, final Bitmap.CompressFormat fmt, final String mime, final String name) {
        box.addView(pickButton("Pick Image"));
        box.addView(spacer());
        box.addView(pickedCard);
        box.addView(spacer());
        Button go = Ui.button(this, label);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (picked == null) { toast("Pick an image first"); return; }
                Bitmap out = picked;
                if (fmt == Bitmap.CompressFormat.JPEG) out = flatten(picked);
                showResult(out, fmt, 90, mime, name, "Ready to save as " + name);
            }
        });
        box.addView(go);
    }

    private void buildCropper() {
        box.addView(pickButton("Pick Image"));
        box.addView(spacer());
        box.addView(pickedCard);
        box.addView(spacer());
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Crop area in % of image", 13, Ui.DIM, false));
        final EditText l = Ui.input(this, "Left % (default 10)", false);
        final EditText t = Ui.input(this, "Top % (default 10)", false);
        final EditText w = Ui.input(this, "Width % (default 80)", false);
        final EditText h = Ui.input(this, "Height % (default 80)", false);
        c.addView(l);
        c.addView(t);
        c.addView(w);
        c.addView(h);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Crop Image");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (picked == null) { toast("Pick an image first"); return; }
                int pl = clamp(intOf(l, 10), 0, 90);
                int pt = clamp(intOf(t, 10), 0, 90);
                int pw = clamp(intOf(w, 80), 5, 100 - pl);
                int ph = clamp(intOf(h, 80), 5, 100 - pt);
                int x = picked.getWidth() * pl / 100;
                int y = picked.getHeight() * pt / 100;
                int cw = Math.max(1, picked.getWidth() * pw / 100);
                int ch = Math.max(1, picked.getHeight() * ph / 100);
                Bitmap out = Bitmap.createBitmap(picked, x, y, Math.min(cw, picked.getWidth() - x), Math.min(ch, picked.getHeight() - y));
                showResult(out, Bitmap.CompressFormat.PNG, 100, "image/png", "gitoolbox_crop.png", "Cropped " + out.getWidth() + " x " + out.getHeight());
            }
        });
        box.addView(go);
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void buildRotate() {
        box.addView(pickButton("Pick Image"));
        box.addView(spacer());
        box.addView(pickedCard);
        box.addView(spacer());
        LinearLayout c = Ui.card(this);
        final EditText d = Ui.input(this, "Degrees (e.g. 90, 180, 270, 45)", false);
        d.setText("90");
        c.addView(d);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Rotate Image");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (picked == null) { toast("Pick an image first"); return; }
                float deg = intOf(d, 90);
                Matrix m = new Matrix();
                m.postRotate(deg);
                Bitmap out = Bitmap.createBitmap(picked, 0, 0, picked.getWidth(), picked.getHeight(), m, true);
                showResult(out, Bitmap.CompressFormat.PNG, 100, "image/png", "gitoolbox_rot.png", "Rotated " + (int) deg + " degrees");
            }
        });
        box.addView(go);
    }

    private void buildFlip() {
        box.addView(pickButton("Pick Image"));
        box.addView(spacer());
        box.addView(pickedCard);
        box.addView(spacer());
        Button fh = Ui.button(this, "Flip Horizontal");
        fh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flip(true);
            }
        });
        Button fv = Ui.button(this, "Flip Vertical");
        fv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flip(false);
            }
        });
        box.addView(fh);
        box.addView(fv);
    }

    private void flip(boolean horizontal) {
        if (picked == null) { toast("Pick an image first"); return; }
        Matrix m = new Matrix();
        if (horizontal) m.setScale(-1, 1);
        else m.setScale(1, -1);
        Bitmap out = Bitmap.createBitmap(picked, 0, 0, picked.getWidth(), picked.getHeight(), m, true);
        showResult(out, Bitmap.CompressFormat.PNG, 100, "image/png", "gitoolbox_flip.png", "Flipped");
    }

    private void buildB64Enc() {
        box.addView(pickButton("Pick Image"));
        box.addView(spacer());
        box.addView(pickedCard);
        box.addView(spacer());
        Button go = Ui.button(this, "Encode to Base64");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (picked == null) { toast("Pick an image first"); return; }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                picked.compress(Bitmap.CompressFormat.PNG, 100, bos);
                String b64 = android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP);
                showTextResult("data:image/png;base64," + b64, "Encoded: " + b64.length() + " chars (data URI)");
            }
        });
        box.addView(go);
    }

    private void buildB64Dec() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Paste a base64 image string", 13, Ui.DIM, false));
        final EditText et = Ui.input(this, "data:image/png;base64,...", true);
        c.addView(et);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Decode Image");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String s = et.getText().toString().trim();
                if (s.length() == 0) { toast("Paste a base64 string first"); return; }
                int cix = s.indexOf("base64,");
                if (cix >= 0) s = s.substring(cix + 7);
                try {
                    byte[] bytes = android.util.Base64.decode(s, android.util.Base64.DEFAULT);
                    Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (b == null) { toast("Not a valid image"); return; }
                    showResult(b, Bitmap.CompressFormat.PNG, 100, "image/png", "gitoolbox_decoded.png", "Decoded " + b.getWidth() + " x " + b.getHeight());
                } catch (Exception e) {
                    toast("Invalid base64");
                }
            }
        });
        box.addView(go);
    }

    private void buildWatermark() {
        box.addView(pickButton("Pick Image"));
        box.addView(spacer());
        box.addView(pickedCard);
        box.addView(spacer());
        LinearLayout c = Ui.card(this);
        final EditText txt = Ui.input(this, "Watermark text", false);
        txt.setText("GI TOOLBOX");
        c.addView(txt);
        box.addView(c);
        box.addView(spacer());
        LinearLayout r = Ui.row(this);
        String[] posLabels = {"Top Left", "Top Right", "Bottom Left", "Bottom Right"};
        for (int i = 0; i < 4; i++) {
            final int pos = i;
            Button b = Ui.button(this, posLabels[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 8);
            b.setLayoutParams(lp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (picked == null) { toast("Pick an image first"); return; }
                    String s = txt.getText().toString().trim();
                    if (s.length() == 0) s = "GI TOOLBOX";
                    showResult(watermark(picked, s, pos), Bitmap.CompressFormat.PNG, 100, "image/png", "gitoolbox_wm.png", "Watermark added");
                }
            });
            r.addView(b);
        }
        box.addView(r);
    }

    private Bitmap watermark(Bitmap src, String txt, int pos) {
        Bitmap b = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(Math.max(16f, b.getWidth() / 24f));
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setColor(0xFFFFFFFF);
        Paint sh = new Paint(p);
        sh.setStyle(Paint.Style.STROKE);
        sh.setStrokeWidth(Math.max(2f, b.getWidth() / 240f));
        sh.setColor(0x99000000);
        float w = p.measureText(txt);
        Paint.FontMetrics fm = p.getFontMetrics();
        float h = fm.descent - fm.ascent;
        float m = h * 0.5f;
        float x;
        float y;
        if (pos == 0) { x = m; y = m - fm.ascent; }
        else if (pos == 1) { x = b.getWidth() - w - m; y = m - fm.ascent; }
        else if (pos == 2) { x = m; y = b.getHeight() - m; }
        else { x = b.getWidth() - w - m; y = b.getHeight() - m; }
        c.drawText(txt, x, y, sh);
        c.drawText(txt, x, y, p);
        return b;
    }

    private void buildFilter(String label, final int mode) {
        box.addView(pickButton("Pick Image"));
        box.addView(spacer());
        box.addView(pickedCard);
        box.addView(spacer());
        Button go = Ui.button(this, label);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (picked == null) { toast("Pick an image first"); return; }
                Bitmap out = picked.copy(Bitmap.Config.ARGB_8888, true);
                ColorMatrix cm = new ColorMatrix();
                cm.setSaturation(mode == 0 ? 0f : 1f);
                ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColorFilter(f);
                Canvas c = new Canvas(out);
                c.drawBitmap(picked, 0, 0, p);
                showResult(out, Bitmap.CompressFormat.PNG, 100, "image/png", "gitoolbox_gray.png", "Grayscale applied");
            }
        });
        box.addView(go);
    }

    private void buildBrightContrast(final boolean bright) {
        box.addView(pickButton("Pick Image"));
        box.addView(spacer());
        box.addView(pickedCard);
        box.addView(spacer());
        LinearLayout c = Ui.card(this);
        final TextView val = Ui.text(this, bright ? "Brightness: 0" : "Contrast: 0", 13, Ui.TEXT, false);
        c.addView(val);
        final SeekBar sb = new SeekBar(this);
        sb.setMax(200);
        sb.setProgress(100);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                val.setText((bright ? "Brightness: " : "Contrast: ") + (progress - 100));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        c.addView(sb);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, bright ? "Apply Brightness" : "Apply Contrast");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (picked == null) { toast("Pick an image first"); return; }
                int value = sb.getProgress() - 100;
                ColorMatrix cm;
                if (bright) {
                    float o = value * 2.55f;
                    cm = new ColorMatrix(new float[]{
                            1, 0, 0, 0, o,
                            0, 1, 0, 0, o,
                            0, 0, 1, 0, o,
                            0, 0, 0, 1, 0});
                } else {
                    float s = (value + 100) / 100f;
                    float o = 128 * (1 - s);
                    cm = new ColorMatrix(new float[]{
                            s, 0, 0, 0, o,
                            0, s, 0, 0, o,
                            0, 0, s, 0, o,
                            0, 0, 0, 1, 0});
                }
                Bitmap out = Bitmap.createBitmap(picked.getWidth(), picked.getHeight(), Bitmap.Config.ARGB_8888);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColorFilter(new ColorMatrixColorFilter(cm));
                Canvas c2 = new Canvas(out);
                c2.drawBitmap(picked, 0, 0, p);
                showResult(out, Bitmap.CompressFormat.PNG, 100, "image/png", "gitoolbox_adj.png",
                        (bright ? "Brightness " : "Contrast ") + value);
            }
        });
        box.addView(go);
    }

    private void buildColorPicker() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Tap the strip to pick a color", 13, Ui.DIM, false));
        HueStrip strip = new HueStrip(this);
        strip.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 150)));
        c.addView(strip);
        c.addView(Ui.text(this, "Brightness", 12, Ui.DIM, false));
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(100);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                cpValue = progress;
                if (cpHex != null) cpHex.setText(hexOf(pickedHue));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        c.addView(sb);
        cpHex = Ui.text(this, "#00FF9D", 20, Ui.GREEN, true);
        cpHex.setGravity(Gravity.CENTER);
        c.addView(cpHex);
        Button copy = Ui.button(this, "Copy Hex");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cmgr = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cmgr.setPrimaryClip(ClipData.newPlainText("color", hexOf(pickedHue)));
                toast("Copied " + hexOf(pickedHue));
            }
        });
        c.addView(copy);
        box.addView(c);
    }

    private float pickedHue = 160f;

    private String hexOf(float hue) {
        int col = Color.HSVToColor(new float[]{hue, 1f, cpValue / 100f});
        return String.format("#%06X", 0xFFFFFF & col);
    }

    private class HueStrip extends View {
        public HueStrip(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            LinearGradient g = new LinearGradient(0, 0, getWidth(), getHeight(),
                    new int[]{0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000},
                    null, Shader.TileMode.CLAMP);
            Paint p = new Paint();
            p.setShader(g);
            c.drawRect(0, 0, getWidth(), getHeight(), p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float f = Math.max(0f, Math.min(1f, e.getX() / Math.max(1, getWidth())));
            pickedHue = f * 360f;
            if (cpHex != null) cpHex.setText(hexOf(pickedHue));
            return true;
        }
    }

    private void buildPalette() {
        box.addView(pickButton("Pick Image"));
        box.addView(spacer());
        box.addView(pickedCard);
        box.addView(spacer());
        Button go = Ui.button(this, "Generate Palette");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (picked == null) { toast("Pick an image first"); return; }
                resultCard.removeAllViews();
                resultCard.addView(Ui.text(this, "Palette", 15, Ui.GOLD, true));
                List<Integer> colors = palette(picked);
                for (int i = 0; i < colors.size(); i++) {
                    int col = colors.get(i);
                    LinearLayout r = Ui.row(this);
                    r.setGravity(Gravity.CENTER_VERTICAL);
                    View sw = new View(ImageToolActivity.this);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(ImageToolActivity.this, 46), Ui.dp(ImageToolActivity.this, 46));
                    lp.rightMargin = Ui.dp(ImageToolActivity.this, 12);
                    sw.setLayoutParams(lp);
                    sw.setBackgroundColor(0xFF000000 | col);
                    r.addView(sw);
                    r.addView(Ui.text(ImageToolActivity.this, String.format("#%06X", 0xFFFFFF & col), 14, Ui.TEXT, true));
                    resultCard.addView(r);
                }
                box.addView(resultCard);
            }
        });
        box.addView(go);
    }

    private List<Integer> palette(Bitmap src) {
        Bitmap s = Bitmap.createScaledBitmap(src, 48, 48, true);
        HashMap<Integer, Integer> counts = new HashMap<Integer, Integer>();
        for (int y = 0; y < s.getHeight(); y++) {
            for (int x = 0; x < s.getWidth(); x++) {
                int p = s.getPixel(x, y);
                int r = ((p >> 16) & 0xFF) >> 3 << 3;
                int g = ((p >> 8) & 0xFF) >> 3 << 3;
                int b = (p & 0xFF) >> 3 << 3;
                int key = (r << 16) | (g << 8) | b;
                Integer old = counts.get(key);
                counts.put(key, old == null ? 1 : old + 1);
            }
        }
        List<Map.Entry<Integer, Integer>> list = new ArrayList<Map.Entry<Integer, Integer>>(counts.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<Integer, Integer>>() {
            @Override
            public int compare(Map.Entry<Integer, Integer> a, Map.Entry<Integer, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        List<Integer> out = new ArrayList<Integer>();
        for (int i = 0; i < list.size() && out.size() < 6; i++) {
            int col = list.get(i).getKey();
            boolean near = false;
            for (int j = 0; j < out.size(); j++) {
                int o = out.get(j);
                int dr = ((col >> 16) & 0xFF) - ((o >> 16) & 0xFF);
                int dg = ((col >> 8) & 0xFF) - ((o >> 8) & 0xFF);
                int db = (col & 0xFF) - (o & 0xFF);
                if (Math.abs(dr) + Math.abs(dg) + Math.abs(db) < 90) { near = true; break; }
            }
            if (!near) out.add(col);
        }
        return out;
    }

    private void buildMeme() {
        box.addView(pickButton("Pick Image"));
        box.addView(spacer());
        box.addView(pickedCard);
        box.addView(spacer());
        LinearLayout c = Ui.card(this);
        final EditText top = Ui.input(this, "Top text", false);
        final EditText bot = Ui.input(this, "Bottom text", false);
        c.addView(top);
        c.addView(bot);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Make Meme");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (picked == null) { toast("Pick an image first"); return; }
                String t = top.getText().toString().trim();
                String b = bot.getText().toString().trim();
                if (t.length() == 0 && b.length() == 0) { toast("Enter top or bottom text"); return; }
                showResult(meme(picked, t, b), Bitmap.CompressFormat.PNG, 100, "image/png", "gitoolbox_meme.png", "Meme ready");
            }
        });
        box.addView(go);
    }

    private Bitmap meme(Bitmap src, String top, String bot) {
        Bitmap b = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(b);
        float size = Math.max(20f, b.getWidth() / 9f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        Paint sh = new Paint(p);
        sh.setStyle(Paint.Style.STROKE);
        sh.setStrokeWidth(size / 12f);
        sh.setColor(0xFF000000);
        Paint.FontMetrics fm = p.getFontMetrics();
        if (top.length() > 0) {
            float y = size + size * 0.4f - fm.ascent - fm.ascent;
            c.drawText(top, b.getWidth() / 2f, size * 1.1f, sh);
            c.drawText(top, b.getWidth() / 2f, size * 1.1f, p);
        }
        if (bot.length() > 0) {
            float y2 = b.getHeight() - size * 0.35f;
            c.drawText(bot, b.getWidth() / 2f, y2, sh);
            c.drawText(bot, b.getWidth() / 2f, y2, p);
        }
        return b;
    }

    private void buildSplitter() {
        box.addView(pickButton("Pick Image"));
        box.addView(spacer());
        box.addView(pickedCard);
        box.addView(spacer());
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Split into a grid, pieces are saved to Pictures/GIToolbox", 12, Ui.DIM, false));
        final EditText r = Ui.input(this, "Rows (default 2)", false);
        final EditText col = Ui.input(this, "Columns (default 2)", false);
        c.addView(r);
        c.addView(col);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Split Image");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (picked == null) { toast("Pick an image first"); return; }
                int rows = clamp(intOf(r, 2), 1, 8);
                int cols = clamp(intOf(col, 2), 1, 8);
                int pw = picked.getWidth() / cols;
                int ph = picked.getHeight() / rows;
                int saved = 0;
                for (int ry = 0; ry < rows; ry++) {
                    for (int rx = 0; rx < cols; rx++) {
                        Bitmap piece = Bitmap.createBitmap(picked, rx * pw, ry * ph, pw, ph);
                        saveGallery("GIToolbox_split_" + ry + "_" + rx + ".png", piece);
                        saved++;
                    }
                }
                toast("Saved " + saved + " pieces to Pictures/GIToolbox");
                resultCard.removeAllViews();
                resultCard.addView(Ui.text(this, "Saved " + saved + " images to Pictures/GIToolbox", 14, Ui.GREEN, true));
                box.addView(resultCard);
            }
        });
        box.addView(go);
    }

    private void buildMerger() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Pick two images, then merge them", 13, Ui.DIM, false));
        c.addView(pickButton(10, "Pick Image A"));
        c.addView(pickButton(11, "Pick Image B"));
        box.addView(c);
        box.addView(spacer());
        Button vert = Ui.button(this, "Merge Vertical");
        vert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                merge(true);
            }
        });
        Button hor = Ui.button(this, "Merge Horizontal");
        hor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                merge(false);
            }
        });
        box.addView(vert);
        box.addView(hor);
    }

    private void merge(boolean vertical) {
        if (pickedA == null || pickedB == null) { toast("Pick both images first"); return; }
        Bitmap a = pickedA;
        Bitmap b = pickedB;
        Bitmap out;
        if (vertical) {
            int w = Math.max(a.getWidth(), b.getWidth());
            Bitmap a2 = a.getWidth() == w ? a : Bitmap.createScaledBitmap(a, w, (int) (a.getHeight() * (w / (float) a.getWidth())), true);
            Bitmap b2 = b.getWidth() == w ? b : Bitmap.createScaledBitmap(b, w, (int) (b.getHeight() * (w / (float) b.getWidth())), true);
            out = Bitmap.createBitmap(w, a2.getHeight() + b2.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            c.drawBitmap(a2, 0, 0, null);
            c.drawBitmap(b2, 0, a2.getHeight(), null);
        } else {
            int h = Math.max(a.getHeight(), b.getHeight());
            Bitmap a2 = a.getHeight() == h ? a : Bitmap.createScaledBitmap(a, (int) (a.getWidth() * (h / (float) a.getHeight())), h, true);
            Bitmap b2 = b.getHeight() == h ? b : Bitmap.createScaledBitmap(b, (int) (b.getWidth() * (h / (float) b.getHeight())), h, true);
            out = Bitmap.createBitmap(a2.getWidth() + b2.getWidth(), h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            c.drawBitmap(a2, 0, 0, null);
            c.drawBitmap(b2, a2.getWidth(), 0, null);
        }
        showResult(out, Bitmap.CompressFormat.PNG, 100, "image/png", "gitoolbox_merge.png", "Merged " + out.getWidth() + " x " + out.getHeight());
    }

    // ---------------------------------------------------------------- result

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == 1) {
            pickMain(data.getData());
        } else if (req == 2) {
            writeSave(data.getData());
        } else if (req == 10) {
            pickedA = safeLoad(data.getData());
            toast("Image A: " + (pickedA == null ? "failed" : pickedA.getWidth() + " x " + pickedA.getHeight()));
        } else if (req == 11) {
            pickedB = safeLoad(data.getData());
            toast("Image B: " + (pickedB == null ? "failed" : pickedB.getWidth() + " x " + pickedB.getHeight()));
        }
    }

    private void writeSave(Uri u) {
        try {
            OutputStream os = getContentResolver().openOutputStream(u);
            if (pendingText != null) {
                os.write(pendingText.getBytes("UTF-8"));
                os.close();
                toast("Saved");
                return;
            }
            if (pendingResult != null) {
                pendingResult.compress(pendingFormat, pendingQuality, os);
                os.close();
                toast("Saved to " + u.getLastPathSegment());
            }
        } catch (Exception e) {
            toast("Save failed: " + e.getMessage());
        }
    }
}
