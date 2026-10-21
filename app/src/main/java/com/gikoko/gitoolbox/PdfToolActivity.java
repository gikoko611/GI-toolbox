package com.gikoko.gitoolbox;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Native PDF tools: create PDFs from text/markdown/images, render PDFs to images, viewer. */
public class PdfToolActivity extends Activity {

    private static final int W = 595;
    private static final int H = 842;

    private String tool;
    private PdfDocument pendingDoc;
    private LinearLayout box;
    private LinearLayout resultCard;
    private Bitmap pickedImage;
    private List<Uri> pickedImages = new ArrayList<Uri>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tool = getIntent() == null ? null : getIntent().getStringExtra("tool");
        if (tool == null) tool = "PDF Tools";
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
        resultCard = Ui.card(this);
        build();
    }

    private void build() {
        if (tool.equals("Text \u2192 PDF")) buildTextPdf(false);
        else if (tool.equals("Markdown \u2192 PDF")) buildTextPdf(true);
        else if (tool.equals("Image \u2192 PDF")) buildImagePdf();
        else if (tool.equals("Images \u2192 PDF")) buildImagesPdf();
        else if (tool.equals("PDF \u2192 Images")) buildPdfRead(true);
        else if (tool.equals("PDF Viewer")) buildPdfRead(false);
        else if (tool.equals("PDF Metadata Editor")) buildPdfRead(false);
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

    private void toast(String s) {
        Toast.makeText(PdfToolActivity.this, s, Toast.LENGTH_SHORT).show();
    }

    private float floatOf(EditText e, float def) {
        try {
            return Float.parseFloat(e.getText().toString().trim());
        } catch (Exception ex) {
            return def;
        }
    }

    // ------------------------------------------------------------ text -> pdf

    private void buildTextPdf(final boolean md) {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, md ? "Type markdown (# Title, **bold**, - bullet)" : "Type your text", 13, Ui.DIM, false));
        final EditText et = Ui.input(this, "Content...", true);
        et.setMinLines(10);
        c.addView(et);
        if (md) {
            et.setText("# GI Toolbox\n\n- Native PDF writer\n- **Bold** text\n- Made for Android\n");
        }
        final EditText size = Ui.input(this, "Font size (default 11)", false);
        size.setText("11");
        c.addView(size);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Create PDF");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = et.getText().toString();
                if (text.trim().length() == 0) { toast("Type some text first"); return; }
                float fs = floatOf(size, 11f);
                if (fs < 5) fs = 5;
                if (fs > 40) fs = 40;
                pendingDoc = makeTextPdf(text, fs, md);
                if (pendingDoc == null) { toast("Failed to build PDF"); return; }
                savePdf("gitoolbox.pdf");
            }
        });
        box.addView(go);
    }

    private PdfDocument makeTextPdf(String text, float baseSize, boolean md) {
        try {
            PdfDocument doc = new PdfDocument();
            int pageNum = 1;
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNum).create());
            Canvas canvas = page.getCanvas();
            float y = 50;
            String[] lines = text.replace("\r", "").split("\n", -1);
            for (int li = 0; li < lines.length; li++) {
                String line = lines[li];
                float size = baseSize;
                if (md) {
                    String raw = line;
                    if (raw.startsWith("### ")) { size = baseSize * 1.25f; line = raw.substring(4); }
                    else if (raw.startsWith("## ")) { size = baseSize * 1.5f; line = raw.substring(3); }
                    else if (raw.startsWith("# ")) { size = baseSize * 1.8f; line = raw.substring(2); }
                    line = line.replace("**", "");
                    line = line.replace("`", "");
                    if (line.startsWith("- ")) line = "\u2022 " + line.substring(2);
                }
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(0xFF101010);
                p.setTextSize(size);
                if (md && line.startsWith("\u2022 ")) p.setTypeface(Typeface.DEFAULT);
                List<String> wrapped = wrap(p, line, W - 100);
                for (int wi = 0; wi < wrapped.size(); wi++) {
                    if (y > H - 50) {
                        doc.finishPage(page);
                        pageNum++;
                        page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNum).create());
                        canvas = page.getCanvas();
                        y = 50;
                    }
                    canvas.drawText(wrapped.get(wi), 50, y, p);
                    y += size * 1.5f;
                }
            }
            doc.finishPage(page);
            return doc;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> wrap(Paint p, String line, float maxWidth) {
        List<String> out = new ArrayList<String>();
        if (line.length() == 0) {
            out.add("");
            return out;
        }
        String[] words = line.split(" ");
        String cur = "";
        for (int i = 0; i < words.length; i++) {
            String test = cur.length() == 0 ? words[i] : cur + " " + words[i];
            if (p.measureText(test) > maxWidth && cur.length() > 0) {
                out.add(cur);
                cur = words[i];
            } else {
                cur = test;
            }
        }
        if (cur.length() > 0) out.add(cur);
        return out;
    }

    // ------------------------------------------------------------ image -> pdf

    private void buildImagePdf() {
        box.addView(pickButton(1, "Pick Image"));
        box.addView(spacer());
        box.addView(pickedCard());
        box.addView(spacer());
        Button go = Ui.button(this, "Create PDF");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedImage == null) { toast("Pick an image first"); return; }
                pendingDoc = imagePdfOne(pickedImage);
                savePdf("gitoolbox_image.pdf");
            }
        });
        box.addView(go);
    }

    private void buildImagesPdf() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Pick multiple images (each becomes a page)", 13, Ui.DIM, false));
        Button pick = Ui.button(this, "Pick Images", Ui.GOLD);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("image/*");
                it.putExtra("android.intent.extra.ALLOW_MULTIPLE", true);
                startActivityForResult(it, 4);
            }
        });
        c.addView(pick);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Create PDF");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedImages.size() == 0) { toast("Pick images first"); return; }
                PdfDocument doc = new PdfDocument();
                int pageNum = 1;
                for (int i = 0; i < pickedImages.size(); i++) {
                    Bitmap b = safeLoad(pickedImages.get(i));
                    if (b == null) continue;
                    PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNum).create());
                    drawCentered(page.getCanvas(), b);
                    doc.finishPage(page);
                    pageNum++;
                }
                pendingDoc = doc;
                savePdf("gitoolbox_images.pdf");
            }
        });
        box.addView(go);
    }

    private PdfDocument imagePdfOne(Bitmap img) {
        PdfDocument doc = new PdfDocument();
        PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, 1).create());
        drawCentered(page.getCanvas(), img);
        doc.finishPage(page);
        return doc;
    }

    private void drawCentered(Canvas c, Bitmap img) {
        float iw = img.getWidth();
        float ih = img.getHeight();
        float scale = Math.min((W - 60f) / iw, (H - 60f) / ih);
        float dw = iw * scale;
        float dh = ih * scale;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(img, (W - dw) / 2f, (H - dh) / 2f, p);
    }

    // ------------------------------------------------------------ pdf reading

    private void buildPdfRead(final boolean saveImages) {
        LinearLayout c = Ui.card(this);
        if (saveImages) {
            c.addView(Ui.text(this, "Pick a PDF, every page is saved as PNG to Pictures/GIToolbox", 13, Ui.DIM, false));
        } else {
            c.addView(Ui.text(this, tool.equals("PDF Viewer") ? "Pick a PDF to view its pages" : "Pick a PDF to inspect it", 13, Ui.DIM, false));
        }
        Button pick = Ui.button(this, "Pick PDF", Ui.GOLD);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("application/pdf");
                startActivityForResult(it, 5);
            }
        });
        c.addView(pick);
        box.addView(c);
        box.addView(spacer());
        box.addView(resultCard);
    }

    private void renderPdf(Uri u, boolean saveImages) {
        try {
            ParcelFileDescriptor pf = getContentResolver().openFileDescriptor(u, "r");
            if (pf == null) { toast("Cannot open PDF"); return; }
            PdfRenderer r = new PdfRenderer(pf);
            int n = r.getPageCount();
            resultCard.removeAllViews();
            resultCard.addView(Ui.text(this, "Pages: " + n, 15, Ui.GREEN, true));
            PdfRenderer.Page pg0 = r.openPage(0);
            resultCard.addView(Ui.text(this, "Page size: " + pg0.getWidth() + " x " + pg0.getHeight() + " pt", 12, Ui.DIM, false));
            pg0.close();
            if (!saveImages && !tool.equals("PDF Metadata Editor")) {
                int lim = Math.min(n, 20);
                for (int i = 0; i < lim; i++) {
                    PdfRenderer.Page pg = r.openPage(i);
                    Bitmap b = renderPage(pg);
                    resultCard.addView(preview(b));
                    pg.close();
                }
                resultCard.addView(Ui.text(this, n > 20 ? "(showing first 20 of " + n + " pages)" : "End of document", 12, Ui.DIM, false));
            } else if (saveImages) {
                int saved = 0;
                for (int i = 0; i < n; i++) {
                    PdfRenderer.Page pg = r.openPage(i);
                    Bitmap b = renderPage(pg);
                    pg.close();
                    saveGallery("GIToolbox_page_" + (i + 1) + ".png", b);
                    saved++;
                }
                resultCard.addView(Ui.text(this, "Saved " + saved + " pages to Pictures/GIToolbox", 13, Ui.GOLD, true));
            } else {
                resultCard.addView(Ui.text(this, "Metadata editing ships with the next APK update. Page count and size shown above.", 12, Ui.TEXT, false));
            }
            r.close();
            pf.close();
            toast("PDF loaded");
        } catch (Exception e) {
            toast("Error: " + e.getMessage());
        }
    }

    private Bitmap renderPage(PdfRenderer.Page pg) {
        int w = Math.max(1, pg.getWidth() * 2);
        int h = Math.max(1, pg.getHeight() * 2);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(Color.WHITE);
        pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        return b;
    }

    // ------------------------------------------------------------ shared helpers

    private LinearLayout pickedCardField;

    private LinearLayout pickedCard() {
        if (pickedCardField == null) pickedCardField = Ui.card(this);
        return pickedCardField;
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

    private Bitmap safeLoad(Uri u) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            java.io.InputStream is = getContentResolver().openInputStream(u);
            BitmapFactory.decodeStream(is, null, o);
            is.close();
            int s = 1;
            while (o.outWidth / s > 1600 || o.outHeight / s > 1600) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            is = getContentResolver().openInputStream(u);
            Bitmap b = BitmapFactory.decodeStream(is, null, o2);
            is.close();
            return b;
        } catch (Exception e) {
            return null;
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
        return iv;
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
            if (u == null) { toast("Save failed"); return; }
            OutputStream os = getContentResolver().openOutputStream(u);
            b.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.close();
        } catch (Exception e) {
            toast("Save failed: " + e.getMessage());
        }
    }

    private void savePdf(String name) {
        Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("application/pdf");
        it.putExtra("android.intent.extra.TITLE", name);
        startActivityForResult(it, 2);
    }

    // ------------------------------------------------------------ result

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == 1) {
            pickedImage = safeLoad(data.getData());
            pickedCard().removeAllViews();
            if (pickedImage != null) pickedCard().addView(preview(pickedImage));
            else toast("Could not load image");
        } else if (req == 2) {
            writePdf(data.getData());
        } else if (req == 4) {
            pickedImages.clear();
            android.content.ClipData cd = data.getClipData();
            if (cd != null) {
                for (int i = 0; i < cd.getItemCount(); i++) {
                    pickedImages.add(cd.getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                pickedImages.add(data.getData());
            }
            toast(pickedImages.size() + " image(s) selected");
        } else if (req == 5) {
            renderPdf(data.getData(), tool.equals("PDF \u2192 Images"));
        }
    }

    private void writePdf(Uri u) {
        if (pendingDoc == null) return;
        try {
            OutputStream os = getContentResolver().openOutputStream(u);
            pendingDoc.writeTo(os);
            os.close();
            pendingDoc.close();
            pendingDoc = null;
            toast("PDF saved");
        } catch (Exception e) {
            toast("Save failed: " + e.getMessage());
        }
    }
}
