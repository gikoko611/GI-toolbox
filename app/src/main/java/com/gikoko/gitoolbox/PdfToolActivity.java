package com.gikoko.gitoolbox;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Native PDF tools v3.0: create, render, rebuild (merge/split/rotate/crop/watermark), convert. */
public class PdfToolActivity extends Activity {

    private static final int W = 595;
    private static final int H = 842;

    private String tool;
    private PdfDocument pendingDoc;
    private LinearLayout box;
    private LinearLayout resultCard;
    private Bitmap pickedImage;
    private List<Uri> pickedImages = new ArrayList<Uri>();
    private Uri pickedPdf;
    private Uri secondPdf;
    private Uri pickedDocx;
    private String pendingDocHtml;
    private String pendingDocName = "gitoolbox.doc";

    // codes: 1 image, 2 save pdf, 4 images, 5 pdf main, 6 pdf second, 7 docx, 8 save doc

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
        else if (tool.equals("HTML \u2192 PDF")) buildHtmlPdf();
        else if (tool.equals("Excel \u2192 PDF")) buildCsvPdf();
        else if (tool.equals("Image \u2192 PDF")) buildImagePdf();
        else if (tool.equals("Images \u2192 PDF")) buildImagesPdf();
        else if (tool.equals("Word \u2192 PDF")) buildDocxPdf();
        else if (tool.equals("PDF \u2192 Word")) buildPdfToWord();
        else if (tool.equals("PDF \u2192 Images")) buildPdfRead(true);
        else if (tool.equals("PDF Viewer")) buildPdfRead(false);
        else if (tool.equals("PDF Metadata Editor")) buildPdfRead(false);
        else if (tool.equals("PDF Merger")) buildMerger();
        else if (tool.equals("PDF Splitter")) buildOrderTool("Pages to extract, e.g. 1-3 or 1,2,5 (blank = all pages)");
        else if (tool.equals("Delete PDF Pages")) buildOrderTool("Pages to DELETE, e.g. 2,5");
        else if (tool.equals("Rotate PDF Pages")) buildRotate();
        else if (tool.equals("Reorder Pages")) buildOrderTool("New order, e.g. 3,1,2");
        else if (tool.equals("Crop PDF Pages")) buildCrop();
        else if (tool.equals("PDF Watermark")) buildWatermark();
        else if (tool.equals("PDF Annotation")) buildAnnotate();
        else if (tool.equals("PDF Compressor")) buildCompress();
        else addPanelNote();
    }

    private void addPanelNote() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, tool, 16, Ui.GOLD, true));
        String msg;
        if (tool.equals("Extract PDF Text") || tool.equals("PDF \u2192 Text")) {
            msg = "Android's built-in PDF engine renders pages as images, so text embedded in a PDF cannot be pulled out on-device.\n\nWorkaround: use PDF \u2192 Images or PDF Viewer here, then use an OCR app to read the text from the images.";
        } else if (tool.equals("PDF Password Lock") || tool.equals("PDF Password Unlock")) {
            msg = "Password protection needs a full PDF writer library, which ships with the next APK update.\n\nTip: for now you can keep PDFs private with the App Lock tool (Security category).";
        } else {
            msg = "This tool needs a full PDF structure editor, which ships with the next APK update.\nEverything else in the PDF category works right now.";
        }
        c.addView(Ui.text(this, msg, 14, Ui.TEXT, false));
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

    private int intOf(EditText e, int def) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (Exception ex) {
            return def;
        }
    }

    private Button pickPdf(final int code, String label) {
        Button b = Ui.button(this, label, Ui.GOLD);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("application/pdf");
                startActivityForResult(it, code);
            }
        });
        return b;
    }

    private void showMsg(String s) {
        resultCard.removeAllViews();
        resultCard.addView(Ui.text(this, s, 13, Ui.GREEN, false));
        box.addView(resultCard);
    }

    private void runLong(final String doing, final Runnable r) {
        toast(doing);
        new Thread() {
            @Override
            public void run() {
                try {
                    r.run();
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toast("Failed: " + e.getMessage());
                        }
                    });
                }
            }
        }.start();
    }

    // ------------------------------------------------------------ rebuild engine

    /**
     * Re-render pages of the given PDFs into a new document.
     * order = 1-based page list (null = all pages of each pdf, in sequence).
     */
    private PdfDocument rebuild(List<Uri> pdfs, int[] order, int rotateDeg, int cropPct,
                                String watermark, String note, float scale) throws Exception {
        PdfDocument doc = new PdfDocument();
        int pageNum = 1;
        for (int pi = 0; pi < pdfs.size(); pi++) {
            Uri u = pdfs.get(pi);
            ParcelFileDescriptor pf = getContentResolver().openFileDescriptor(u, "r");
            if (pf == null) throw new Exception("Cannot open PDF");
            PdfRenderer r = new PdfRenderer(pf);
            int n = r.getPageCount();
            int[] idxs;
            if (order == null) {
                idxs = new int[n];
                for (int i = 0; i < n; i++) idxs[i] = i + 1;
            } else {
                idxs = order;
            }
            for (int oi = 0; oi < idxs.length; oi++) {
                int i = idxs[oi] - 1;
                if (i < 0 || i >= n) continue;
                PdfRenderer.Page pg = r.openPage(i);
                int pw = pg.getWidth();
                int ph = pg.getHeight();
                int bw = Math.max(1, (int) (pw * scale));
                int bh = Math.max(1, (int) (ph * scale));
                Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                b.eraseColor(Color.WHITE);
                pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                pg.close();
                int outW = pw;
                int outH = ph;
                if (rotateDeg == 90 || rotateDeg == 270) {
                    outW = ph;
                    outH = pw;
                }
                PdfDocument.PageInfo.Builder info = new PdfDocument.PageInfo.Builder(outW, outH, pageNum);
                PdfDocument.Page page = doc.startPage(info.create());
                Canvas c = page.getCanvas();
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                Matrix m = new Matrix();
                float zoom = 1f / scale;
                if (cropPct > 0) {
                    m.setTranslate(-bw * cropPct / 100f, -bh * cropPct / 100f);
                    zoom /= (1f - 2f * cropPct / 100f);
                }
                m.postScale(zoom, zoom);
                if (rotateDeg == 90) {
                    m.postRotate(90);
                    m.postTranslate(outW, 0);
                } else if (rotateDeg == 180) {
                    m.postRotate(180);
                    m.postTranslate(outW, outH);
                } else if (rotateDeg == 270) {
                    m.postRotate(270);
                    m.postTranslate(0, outH);
                }
                c.drawBitmap(b, m, p);
                if (watermark != null && watermark.length() > 0) {
                    c.save();
                    c.rotate(-40, outW / 2f, outH / 2f);
                    Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
                    wp.setColor(0x30888888);
                    wp.setTextSize(Math.max(24f, outW / 10f));
                    wp.setTypeface(Typeface.DEFAULT_BOLD);
                    float tw = wp.measureText(watermark);
                    c.drawText(watermark, (outW - tw) / 2f, outH / 2f, wp);
                    c.restore();
                }
                if (note != null && note.length() > 0) {
                    Paint np = new Paint(Paint.ANTI_ALIAS_FLAG);
                    np.setColor(0xFFB03030);
                    np.setTextSize(Math.max(10f, outW / 45f));
                    c.drawText(note, 24, outH - 20, np);
                }
                doc.finishPage(page);
                pageNum++;
            }
            r.close();
            pf.close();
        }
        return doc;
    }

    private int[] parsePages(String spec, int n, boolean deletion) throws Exception {
        List<Integer> list = new ArrayList<Integer>();
        if (spec.contains("-")) {
            String[] r = spec.split("-");
            int a = Integer.parseInt(r[0].trim());
            int b = Integer.parseInt(r[1].trim());
            for (int i = Math.max(1, a); i <= Math.min(n, b); i++) list.add(i);
        } else {
            for (String s : spec.split(",")) {
                try {
                    int v = Integer.parseInt(s.trim());
                    if (v >= 1 && v <= n) list.add(v);
                } catch (Exception ignore) {
                }
            }
        }
        if (list.isEmpty()) throw new Exception("No valid pages (1-" + n + ")");
        if (deletion) {
            List<Integer> keep = new ArrayList<Integer>();
            for (int i = 1; i <= n; i++) if (!list.contains(i)) keep.add(i);
            if (keep.isEmpty()) throw new Exception("You cannot delete every page");
            list = keep;
        }
        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i);
        return out;
    }

    private int[] allPages(int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = i + 1;
        return out;
    }

    private int pdfPageCount(Uri u) throws Exception {
        ParcelFileDescriptor pf = getContentResolver().openFileDescriptor(u, "r");
        if (pf == null) throw new Exception("Cannot open PDF");
        PdfRenderer r = new PdfRenderer(pf);
        int n = r.getPageCount();
        r.close();
        pf.close();
        return n;
    }

    private long sizeOf(Uri u) {
        try {
            Cursor c = getContentResolver().query(u, null, null, null, null);
            if (c != null) {
                int ci = c.getColumnIndex(OpenableColumns.SIZE);
                long v = -1;
                if (ci >= 0 && c.moveToFirst()) v = c.getLong(ci);
                c.close();
                return v;
            }
        } catch (Exception ignore) {
        }
        return -1;
    }

    // ------------------------------------------------------------ rebuild tools

    private void buildMerger() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Pick two PDFs — all pages are combined into one file (PDF A first).", 13, Ui.DIM, false));
        c.addView(pickPdf(5, "Pick PDF A"));
        c.addView(spacer());
        c.addView(pickPdf(6, "Pick PDF B"));
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Merge PDFs");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedPdf == null || secondPdf == null) { toast("Pick both PDFs first"); return; }
                runLong("Merging…", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            List<Uri> both = new ArrayList<Uri>();
                            both.add(pickedPdf);
                            both.add(secondPdf);
                            final PdfDocument d = rebuild(both, null, 0, 0, null, null, 2f);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pendingDoc = d;
                                    savePdf("gitoolbox_merged.pdf");
                                }
                            });
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    toast("Merge failed: " + e.getMessage());
                                }
                            });
                        }
                    }
                });
            }
        });
        box.addView(go);
    }

    private void buildOrderTool(String hint) {
        LinearLayout c = Ui.card(this);
        c.addView(pickPdf(5, "Pick PDF"));
        c.addView(spacer());
        final EditText spec = Ui.input(this, hint, false);
        c.addView(spec);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Process PDF");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedPdf == null) { toast("Pick a PDF first"); return; }
                final String s = spec.getText().toString().trim();
                runLong("Working…", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final int n = pdfPageCount(pickedPdf);
                            final int[] order;
                            if (tool.equals("Delete PDF Pages")) {
                                if (s.length() == 0) throw new Exception("Enter pages to delete");
                                order = parsePages(s, n, true);
                            } else if (tool.equals("Reorder Pages")) {
                                if (s.length() == 0) throw new Exception("Enter the new order");
                                order = parsePages(s, n, false);
                                if (order.length != n) throw new Exception("Order must list all " + n + " pages");
                            } else { // Splitter: keep selected pages
                                order = s.length() == 0 ? allPages(n) : parsePages(s, n, false);
                            }
                            final PdfDocument d = rebuild(toList(pickedPdf), order, 0, 0, null, null, 2f);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pendingDoc = d;
                                    savePdf("gitoolbox_pages.pdf");
                                }
                            });
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    toast("Failed: " + e.getMessage());
                                }
                            });
                        }
                    }
                });
            }
        });
        box.addView(go);
    }

    private List<Uri> toList(Uri u) {
        List<Uri> l = new ArrayList<Uri>();
        l.add(u);
        return l;
    }

    private void buildRotate() {
        LinearLayout c = Ui.card(this);
        c.addView(pickPdf(5, "Pick PDF"));
        c.addView(spacer());
        final EditText deg = Ui.input(this, "Degrees: 90 / 180 / 270 (default 90)", false);
        deg.setText("90");
        c.addView(deg);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Rotate Pages");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedPdf == null) { toast("Pick a PDF first"); return; }
                final int d = intOf(deg, 90) % 360;
                runLong("Rotating…", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final PdfDocument doc = rebuild(toList(pickedPdf), null, d, 0, null, null, 2f);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pendingDoc = doc;
                                    savePdf("gitoolbox_rotated.pdf");
                                }
                            });
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    toast("Failed: " + e.getMessage());
                                }
                            });
                        }
                    }
                });
            }
        });
        box.addView(go);
    }

    private void buildCrop() {
        LinearLayout c = Ui.card(this);
        c.addView(pickPdf(5, "Pick PDF"));
        c.addView(spacer());
        final EditText pct = Ui.input(this, "Trim margin % each side: 1-40 (default 8)", false);
        pct.setText("8");
        c.addView(pct);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Crop Pages");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedPdf == null) { toast("Pick a PDF first"); return; }
                final int m = Math.max(1, Math.min(40, intOf(pct, 8)));
                runLong("Cropping…", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final PdfDocument doc = rebuild(toList(pickedPdf), null, 0, m, null, null, 2f);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pendingDoc = doc;
                                    savePdf("gitoolbox_cropped.pdf");
                                }
                            });
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    toast("Failed: " + e.getMessage());
                                }
                            });
                        }
                    }
                });
            }
        });
        box.addView(go);
    }

    private void buildWatermark() {
        LinearLayout c = Ui.card(this);
        c.addView(pickPdf(5, "Pick PDF"));
        c.addView(spacer());
        final EditText txt = Ui.input(this, "Watermark text", false);
        txt.setText("GI TOOLBOX");
        c.addView(txt);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Add Watermark");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedPdf == null) { toast("Pick a PDF first"); return; }
                final String t = txt.getText().toString().trim();
                if (t.length() == 0) { toast("Enter watermark text"); return; }
                runLong("Stamping…", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final PdfDocument doc = rebuild(toList(pickedPdf), null, 0, 0, t, null, 2f);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pendingDoc = doc;
                                    savePdf("gitoolbox_watermark.pdf");
                                }
                            });
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    toast("Failed: " + e.getMessage());
                                }
                            });
                        }
                    }
                });
            }
        });
        box.addView(go);
    }

    private void buildAnnotate() {
        LinearLayout c = Ui.card(this);
        c.addView(pickPdf(5, "Pick PDF"));
        c.addView(spacer());
        final EditText txt = Ui.input(this, "Note stamped on every page", false);
        txt.setText("Reviewed with GIToolbox");
        c.addView(txt);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Annotate PDF");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedPdf == null) { toast("Pick a PDF first"); return; }
                final String t = txt.getText().toString().trim();
                if (t.length() == 0) { toast("Enter a note"); return; }
                runLong("Annotating…", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final PdfDocument doc = rebuild(toList(pickedPdf), null, 0, 0, null, t, 2f);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pendingDoc = doc;
                                    savePdf("gitoolbox_annotated.pdf");
                                }
                            });
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    toast("Failed: " + e.getMessage());
                                }
                            });
                        }
                    }
                });
            }
        });
        box.addView(go);
    }

    private void buildCompress() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Re-renders every page at lower resolution — big PDFs shrink a lot.", 13, Ui.DIM, false));
        c.addView(pickPdf(5, "Pick PDF"));
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Compress PDF");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedPdf == null) { toast("Pick a PDF first"); return; }
                runLong("Compressing…", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            long orig = sizeOf(pickedPdf);
                            final PdfDocument doc = rebuild(toList(pickedPdf), null, 0, 0, null, null, 1.4f);
                            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                            doc.writeTo(bos);
                            final long now = bos.size();
                            doc.close();
                            final String report = "Original: " + (orig > 0 ? Compute.fmtBytes(orig) : "?")
                                    + "  →  New: " + Compute.fmtBytes(now)
                                    + (orig > 0 ? "  (" + (now * 100 / orig) + "% of original)" : "");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // rebuild doc for saving (writeTo consumed nothing; PdfDocument can be written again)
                                    try {
                                        pendingDoc = rebuild(toList(pickedPdf), null, 0, 0, null, null, 1.4f);
                                    } catch (Exception ignore) {
                                        pendingDoc = null;
                                    }
                                    showMsg(report);
                                    savePdf("gitoolbox_compressed.pdf");
                                }
                            });
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    toast("Failed: " + e.getMessage());
                                }
                            });
                        }
                    }
                });
            }
        });
        box.addView(go);
    }

    // ------------------------------------------------------------ docx / csv / html

    private void buildDocxPdf() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Pick a .docx (Word 2007+) file — its text is laid out into a PDF.", 13, Ui.DIM, false));
        Button pick = Ui.button(this, "Pick DOCX", Ui.GOLD);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                startActivityForResult(it, 7);
            }
        });
        c.addView(pick);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Convert to PDF");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedDocx == null) { toast("Pick a .docx first"); return; }
                runLong("Converting…", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final String text = docxText(pickedDocx);
                            if (text.trim().length() == 0) throw new Exception("No text found inside the docx");
                            final PdfDocument d = makeTextPdf(text, 11f, false);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pendingDoc = d;
                                    savePdf("gitoolbox_word.pdf");
                                }
                            });
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    toast("Failed: " + e.getMessage());
                                }
                            });
                        }
                    }
                });
            }
        });
        box.addView(go);
    }

    private String docxText(Uri u) throws Exception {
        InputStream is = getContentResolver().openInputStream(u);
        ZipInputStream z = new ZipInputStream(is);
        ZipEntry e;
        String xml = null;
        while ((e = z.getNextEntry()) != null) {
            if (e.getName().equals("word/document.xml")) {
                StringBuilder sb = new StringBuilder();
                BufferedReader r = new BufferedReader(new InputStreamReader(z, "UTF-8"));
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
                xml = sb.toString();
                break;
            }
        }
        z.close();
        if (xml == null) throw new Exception("Not a valid .docx (missing document.xml)");
        xml = xml.replace("</w:p>", "\n")
                .replace("<w:br/>", "\n")
                .replace("<w:tab/>", "\t")
                .replaceAll("<[^>]+>", "");
        xml = xml.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'");
        return xml;
    }

    private void buildHtmlPdf() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Paste HTML — tags are stripped and the text is laid out as a PDF.", 13, Ui.DIM, false));
        final EditText et = Ui.input(this, "<h1>Title</h1><p>Paragraph…</p>", true);
        et.setMinLines(8);
        c.addView(et);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Convert to PDF");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String html = et.getText().toString();
                if (html.trim().length() == 0) { toast("Paste some HTML first"); return; }
                String text = html.replaceAll("(?i)<br\\s*/?>", "\n")
                        .replaceAll("(?i)</p>", "\n\n")
                        .replaceAll("(?i)<li[^>]*>", "\u2022 ")
                        .replaceAll("(?i)</tr>", "\n")
                        .replaceAll("(?i)</td>", "    ")
                        .replaceAll("(?i)</h[1-6]>", "\n\n")
                        .replaceAll("<[^>]+>", "");
                text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                        .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'");
                pendingDoc = makeTextPdf(text, 11f, false);
                if (pendingDoc == null) { toast("Failed to build PDF"); return; }
                savePdf("gitoolbox_html.pdf");
            }
        });
        box.addView(go);
    }

    private void buildCsvPdf() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Paste spreadsheet data (CSV: one row per line, columns separated by commas). Rendered as a table PDF.", 13, Ui.DIM, false));
        final EditText et = Ui.input(this, "Name, Price, Qty\nRice, 12000, 2\nOil, 8000, 1", true);
        et.setMinLines(8);
        c.addView(et);
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Create Table PDF");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String csv = et.getText().toString();
                if (csv.trim().length() == 0) { toast("Paste some data first"); return; }
                pendingDoc = makeCsvPdf(csv);
                if (pendingDoc == null) { toast("Failed to build PDF"); return; }
                savePdf("gitoolbox_table.pdf");
            }
        });
        box.addView(go);
    }

    private PdfDocument makeCsvPdf(String csv) {
        try {
            String[] lines = csv.replace("\r", "").split("\n");
            List<String[]> rows = new ArrayList<String[]>();
            int cols = 1;
            for (String ln : lines) {
                if (ln.trim().length() == 0) continue;
                String[] f = ln.split(",");
                if (f.length > cols) cols = f.length;
                rows.add(f);
            }
            PdfDocument doc = new PdfDocument();
            int pageNum = 1;
            float margin = 40;
            float rowH = 26;
            float tableW = W - margin * 2;
            float colW = tableW / cols;
            float y = margin;
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNum).create());
            Canvas canvas = page.getCanvas();
            Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
            cell.setColor(0xFF101010);
            cell.setTextSize(9);
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setColor(0xFF666666);
            line.setStrokeWidth(0.8f);
            for (int ri = 0; ri < rows.size(); ri++) {
                if (y + rowH > H - margin) {
                    doc.finishPage(page);
                    pageNum++;
                    page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNum).create());
                    canvas = page.getCanvas();
                    y = margin;
                }
                String[] row = rows.get(ri);
                boolean head = ri == 0;
                cell.setTypeface(head ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                if (head) {
                    android.graphics.Paint fill = new Paint();
                    fill.setColor(0xFFE8F5EE);
                    canvas.drawRect(margin, y, margin + tableW, y + rowH, fill);
                }
                for (int ci = 0; ci < cols; ci++) {
                    String v = ci < row.length ? row[ci].trim() : "";
                    float tx = margin + ci * colW + 6;
                    if (cell.measureText(v) > colW - 10) {
                        while (v.length() > 1 && cell.measureText(v) > colW - 10) v = v.substring(0, v.length() - 1);
                        v += "…";
                    }
                    canvas.drawText(v, tx, y + 16, cell);
                    canvas.drawLine(margin + (ci + 1) * colW, y, margin + (ci + 1) * colW, y + rowH, line);
                }
                canvas.drawLine(margin, y, margin + tableW, y, line);
                canvas.drawLine(margin, y + rowH, margin + tableW, y + rowH, line);
                y += rowH;
            }
            canvas.drawLine(margin, margin, margin, y, line);
            canvas.drawLine(margin + tableW, margin, margin + tableW, y, line);
            doc.finishPage(page);
            return doc;
        } catch (Exception e) {
            return null;
        }
    }

    private void buildPdfToWord() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Renders every PDF page and wraps them into a .doc file (pages appear as images). Opens in Word / WPS.", 13, Ui.DIM, false));
        c.addView(pickPdf(5, "Pick PDF"));
        box.addView(c);
        box.addView(spacer());
        Button go = Ui.button(this, "Convert to DOC");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickedPdf == null) { toast("Pick a PDF first"); return; }
                runLong("Converting…", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ParcelFileDescriptor pf = getContentResolver().openFileDescriptor(pickedPdf, "r");
                            if (pf == null) throw new Exception("Cannot open PDF");
                            PdfRenderer r = new PdfRenderer(pf);
                            int n = Math.min(r.getPageCount(), 30);
                            StringBuilder html = new StringBuilder();
                            html.append("<html><head><meta charset=\"utf-8\"></head><body>");
                            for (int i = 0; i < n; i++) {
                                PdfRenderer.Page pg = r.openPage(i);
                                int bw = Math.max(1, pg.getWidth() * 2);
                                int bh = Math.max(1, pg.getHeight() * 2);
                                Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                                b.eraseColor(Color.WHITE);
                                pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                                pg.close();
                                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                                b.compress(Bitmap.CompressFormat.JPEG, 80, bos);
                                String b64 = android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP);
                                html.append("<p><img width=\"620\" src=\"data:image/jpeg;base64,").append(b64).append("\"></p>");
                                b.recycle();
                            }
                            html.append("</body></html>");
                            r.close();
                            pf.close();
                            final String doc = html.toString();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pendingDocHtml = doc;
                                    pendingDocName = "gitoolbox_word.doc";
                                    Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                    it.addCategory(Intent.CATEGORY_OPENABLE);
                                    it.setType("application/msword");
                                    it.putExtra("android.intent.extra.TITLE", pendingDocName);
                                    startActivityForResult(it, 8);
                                }
                            });
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    toast("Failed: " + e.getMessage());
                                }
                            });
                        }
                    }
                });
            }
        });
        box.addView(go);
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
            resultCard.addView(Ui.text(this, "Pages: " + n + "  |  Size: " + (sizeOf(u) > 0 ? Compute.fmtBytes(sizeOf(u)) : "?"), 15, Ui.GREEN, true));
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
                resultCard.addView(Ui.text(this, "Pages and size shown above. Structure editing (title, author tags) needs a full PDF library — ships with the next update.", 12, Ui.TEXT, false));
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
            InputStream is = getContentResolver().openInputStream(u);
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
        if (pendingDoc == null) return;
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
            pickedPdf = data.getData();
            toast("PDF loaded ✓");
            if (tool.equals("PDF \u2192 Images") || tool.equals("PDF Viewer") || tool.equals("PDF Metadata Editor")) {
                renderPdf(pickedPdf, tool.equals("PDF \u2192 Images"));
            }
        } else if (req == 6) {
            secondPdf = data.getData();
            toast("PDF B loaded ✓");
        } else if (req == 7) {
            pickedDocx = data.getData();
            toast("DOCX loaded ✓");
        } else if (req == 8) {
            try {
                OutputStream os = getContentResolver().openOutputStream(data.getData());
                os.write(pendingDocHtml.getBytes("UTF-8"));
                os.close();
                toast("DOC saved ✓");
            } catch (Exception e) {
                toast("Save failed: " + e.getMessage());
            }
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
            toast("PDF saved ✓");
        } catch (Exception e) {
            toast("Save failed: " + e.getMessage());
        }
    }
}
