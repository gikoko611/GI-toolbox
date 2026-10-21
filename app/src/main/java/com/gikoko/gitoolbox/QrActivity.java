package com.gikoko.gitoolbox;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** QR + 1D barcode tools: generate, render and save. */
public class QrActivity extends Activity {

    private String tool;
    private LinearLayout form;
    private EditText[] fields;
    private BarView barView;
    private TextView msg;
    private boolean[][] lastMatrix;
    private String lastLabel;

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

        msg = Ui.text(this, "", 12, Ui.DIM, false);
        LinearLayout msgCard = Ui.card(this);
        msgCard.addView(msg);
        root.addView(msgCard);

        if (isPanelTool(tool)) {
            showPanelMessage(root);
            setContentView(Ui.scroll(this, root));
            return;
        }

        String[][] spec = formSpec(tool);
        form = Ui.card(this);
        fields = new EditText[spec.length];
        for (int i = 0; i < spec.length; i++) {
            if (spec[i][0].length() > 0) {
                form.addView(Ui.text(this, spec[i][0], 11, Ui.GREEN, true));
            }
            EditText e = Ui.input(this, spec[i][1], false);
            if (spec[i].length > 2 && spec[i][2].equals("num")) {
                e.setInputType(InputType.TYPE_CLASS_NUMBER);
            } else if (spec[i].length > 2 && spec[i][2].equals("multi")) {
                e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                e.setMinLines(3);
            }
            form.addView(e);
            fields[i] = e;
        }
        root.addView(form);

        Button gen = Ui.button(this, "⚡ GENERATE");
        gen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generate();
            }
        });
        root.addView(gen);

        barView = new BarView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 300));
        lp.topMargin = Ui.dp(this, 10);
        barView.setLayoutParams(lp);
        root.addView(barView);

        Button save = Ui.button(this, "💾 SAVE PNG", Ui.GOLD);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePng();
            }
        });
        root.addView(save);

        setContentView(Ui.scroll(this, root));
        if (tool.equals("QR Generator") || tool.equals("Text QR")) {
            msg.setText("Type text and tap GENERATE. Max ~106 characters.");
        }
    }

    boolean isPanelTool(String t) {
        return t.equals("QR Scanner") || t.equals("Barcode Scanner") || t.equals("QR with Logo")
                || t.equals("Code 128") || t.equals("PDF417") || t.equals("Aztec Code")
                || t.equals("Data Matrix") || t.equals("Codabar");
    }

    void showPanelMessage(LinearLayout root) {
        if (tool.equals("QR Scanner") || tool.equals("Barcode Scanner")) {
            msg.setText("🔍 " + tool + "\n\nCamera-based scanning needs a native scanner module, which ships in the next APK update.\n\nFor now: generate codes with the other QR tools and scan them with your phone's built-in camera app (Redmi 9A's Mi Camera scans QR codes).");
        } else if (tool.equals("QR with Logo")) {
            msg.setText("Custom logo overlay ships in the next APK update.\n\nTip: generate a normal QR here with 'QR Generator' — small logos in the center are readable by most scanners anyway.");
        } else {
            msg.setText(tool + " generation ships in the next APK update.\n\nAvailable now in this category: QR Generator, WiFi QR, vCard QR, Email/SMS/Phone/Event/Geo QR, Text QR, URL QR, Batch QR, Barcode Generator (Code 39), EAN-13, UPC-A, ITF-14.");
        }
    }

    String[][] formSpec(String t) {
        if (t.equals("WiFi QR")) {
            return new String[][]{{"NETWORK NAME (SSID)", "e.g. MyHomeWiFi"}, {"PASSWORD", "wifi password"},
                    {"ENCRYPTION", "WPA / WEP / nopass (blank = WPA)"}};
        }
        if (t.equals("vCard QR")) {
            return new String[][]{{"NAME", "Full name"}, {"PHONE", "09xxxxxxx"}, {"EMAIL", "name@mail.com"},
                    {"ORGANIZATION", "Company (optional)"}};
        }
        if (t.equals("Email QR")) {
            return new String[][]{{"EMAIL ADDRESS", "to@example.com"}, {"SUBJECT", "Subject (optional)"},
                    {"BODY", "Message (optional)", "multi"}};
        }
        if (t.equals("SMS QR")) {
            return new String[][]{{"PHONE NUMBER", "09xxxxxxx"}, {"MESSAGE", "Text message (optional)", "multi"}};
        }
        if (t.equals("Phone QR")) {
            return new String[][]{{"PHONE NUMBER", "09xxxxxxx"}};
        }
        if (t.equals("Event QR")) {
            return new String[][]{{"TITLE", "Event name"}, {"LOCATION", "Where (optional)"},
                    {"START", "YYYYMMDD HHMM e.g. 20261101 1830"}, {"END", "YYYYMMDD HHMM (optional)"}};
        }
        if (t.equals("Geo QR")) {
            return new String[][]{{"LATITUDE", "e.g. 16.8661"}, {"LONGITUDE", "e.g. 96.1951"}};
        }
        if (t.equals("URL QR")) {
            return new String[][]{{"URL", "https://example.com"}};
        }
        if (t.equals("Batch QR")) {
            return new String[][]{{"ITEMS", "One item per line", "multi"}};
        }
        if (t.equals("Barcode Generator")) {
            return new String[][]{{"TYPE", "code39 / ean13 / upca / itf14"}, {"CONTENT", "text or digits"}};
        }
        if (t.equals("EAN-13")) {
            return new String[][]{{"DIGITS", "12 or 13 digits (check auto)", "num"}};
        }
        if (t.equals("UPC-A")) {
            return new String[][]{{"DIGITS", "11 or 12 digits", "num"}};
        }
        if (t.equals("ITF-14")) {
            return new String[][]{{"DIGITS", "13 or 14 digits", "num"}};
        }
        if (t.equals("Code 39")) {
            return new String[][]{{"TEXT", "UPPERCASE letters and digits"}};
        }
        // QR Generator / Text QR
        return new String[][]{{"TEXT", "Text to encode, max ~106 chars", "multi"}};
    }

    String content() {
        StringBuilder sb = new StringBuilder();
        for (EditText e : fields) sb.append(e.getText().toString().trim());
        return sb.toString();
    }

    void generate() {
        try {
            String payload = payloadFor();
            if (payload.length() == 0) {
                Toast.makeText(this, "Enter the content first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (tool.equals("Barcode Generator") || tool.equals("EAN-13") || tool.equals("UPC-A")
                    || tool.equals("ITF-14") || tool.equals("Code 39")) {
                barView.setBarcode(barcodeSpec(payload));
                msg.setText("Rendering barcode…");
                lastMatrix = null;
                lastLabel = payload;
                return;
            }
            boolean[][] m = QrCode.encode(payload);
            lastMatrix = m;
            lastLabel = payload;
            barView.setQr(m);
            msg.setText("QR generated ✓ (" + m.length + "×" + m.length
                    + (payload.length() > 60 ? ", content: " + payload.substring(0, 60) + "…" : ", content: " + payload) + ")");
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            msg.setText("Error: " + e.getMessage());
        }
    }

    String payloadFor() throws Exception {
        String t = tool;
        if (t.equals("WiFi QR")) {
            String ssid = val(0), pass = val(1), enc = val(2).toUpperCase(Locale.US);
            if (enc.length() == 0) enc = "WPA";
            if (enc.equals("NOPASS")) return "WIFI:T:nopass;S:" + esc(ssid) + ";;";
            return "WIFI:T:" + enc + ";S:" + esc(ssid) + ";P:" + esc(pass) + ";;";
        }
        if (t.equals("vCard QR")) {
            String name = val(0), phone = val(1), email = val(2), org = val(3);
            return "BEGIN:VCARD\nVERSION:3.0\nFN:" + name + (org.length() > 0 ? "\nORG:" + org : "")
                    + (phone.length() > 0 ? "\nTEL:" + phone : "") + (email.length() > 0 ? "\nEMAIL:" + email : "")
                    + "\nEND:VCARD";
        }
        if (t.equals("Email QR")) {
            String to = val(0), subj = val(1), body = val(2);
            String p = "mailto:" + to;
            if (subj.length() > 0 || body.length() > 0) {
                p += "?subject=" + enc(subj) + (body.length() > 0 ? "&body=" + enc(body) : "");
            }
            return p;
        }
        if (t.equals("SMS QR")) {
            return "SMSTO:" + val(0) + ":" + val(1);
        }
        if (t.equals("Phone QR")) {
            return "tel:" + val(0);
        }
        if (t.equals("Event QR")) {
            String title = val(0), loc = val(1), start = val(2).replace(" ", "T"), end = val(3).replace(" ", "T");
            return "BEGIN:VEVENT\nSUMMARY:" + title + (loc.length() > 0 ? "\nLOCATION:" + loc : "")
                    + "\nDTSTART:" + start + (end.length() > 0 ? "\nDTEND:" + end : "") + "\nEND:VEVENT";
        }
        if (t.equals("Geo QR")) {
            return "geo:" + val(0) + "," + val(1);
        }
        if (t.equals("URL QR")) {
            String u = val(0);
            if (!u.startsWith("http") && !u.startsWith("mailto:") && !u.startsWith("tel:")) u = "https://" + u;
            return u;
        }
        if (t.equals("Batch QR")) {
            String[] lines = val(0).split("\n");
            if (lines.length == 0) return "";
            return lines[0].trim();
        }
        return content();
    }

    String val(int i) {
        return i < fields.length ? fields[i].getText().toString().trim() : "";
    }

    String esc(String s) {
        return s.replace(":", "\\:").replace(";", "\\;").replace(",", "\\,");
    }

    String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }

    void savePng() {
        Bitmap bmp = barView.renderBitmap(800);
        if (bmp == null) {
            Toast.makeText(this, "Generate a code first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String ext = (tool.equals("Barcode Generator") || tool.equals("EAN-13") || tool.equals("UPC-A")
                    || tool.equals("ITF-14") || tool.equals("Code 39")) ? "barcode" : "qr";
            String name = "GIToolbox_" + ext + "_" + System.currentTimeMillis() + ".png";
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name);
            cv.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= 29) {
                cv.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GIToolbox");
            }
            android.net.Uri uri = getContentResolver().insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            java.io.OutputStream os = getContentResolver().openOutputStream(uri);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.close();
            Toast.makeText(this, "Saved to Pictures/GIToolbox ✓", Toast.LENGTH_LONG).show();
            // add to QR history
            try {
                ListToolStore.add(this, tool + " → " + (lastLabel == null ? "" : lastLabel));
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---------- barcode specs ----------
    static class BarcodeSpec {
        // patterns: 1 = narrow, 2 = wide; alternating bar,space starting with bar
        String[] chars;   // rendered pattern strings per input char
        String pattern;   // full element string ("1211...")
        boolean isBits;   // true = pattern is raw bits (1 dark, 0 light)

        BarcodeSpec(String pattern, boolean isBits) {
            this.pattern = pattern;
            this.isBits = isBits;
        }
    }

    BarcodeSpec barcodeSpec(String payload) throws Exception {
        String type = tool.equals("Barcode Generator") ? val(0) : tool;
        String data = tool.equals("Barcode Generator") ? val(1) : val(0);
        if (type.contains("39")) return new BarcodeSpec(code39(data), false);
        if (type.contains("ean")) return new BarcodeSpec(ean13(data, false), true);
        if (type.contains("upc")) return new BarcodeSpec(ean13(data, true), true);
        if (type.contains("itf")) return new BarcodeSpec(itf14(data), false);
        throw new Exception("Unknown barcode type");
    }

    static final String[] C39 = {
            "nnnwwnwnn", "wnnwnnnnw", "nnwwnnnnw", "wnwwnnnnn", "nnnwwnnnw",
            "wnnwwnnnn", "nnwwwnnnn", "nnnwnnwnw", "wnnwnnwnn", "nnwwnnwnn",
            "wnnnnwnnw", "nnwnnwnnw", "wnwnnwnnn", "nnnnwwnnw", "wnnnwwnnn",
            "nnwnwwnnn", "nnnnnwwnw", "wnnnnwwnn", "nnwnnwwnn", "nnnnwwwnn",
            "wnnnnnnww", "nnwnnnnww", "wnwnnnnwn", "nnnnwnnww", "wnnnwnnwn",
            "nnwnwnnwn", "nnnnnnwww", "wnnnnnwwn", "nnwnnnwwn", "nnnnwnwwn",
            "nwnnnnwnw", "wwnnnwnnn", "nwwnnwnnn", "nwnnwnwnn" // - . space
    };
    static final String C39_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. ";

    String code39(String text) {
        String t = "*" + text.toUpperCase(Locale.US).replace(" ", " ") + "*";
        StringBuilder els = new StringBuilder();
        for (char c : t.toCharArray()) {
            int idx = C39_CHARS.indexOf(c);
            if (idx < 0) idx = 36; // unknown -> space
            String p = C39[idx];
            for (int i = 0; i < 9; i++) {
                els.append(p.charAt(i) == 'w' ? 2 : 1);
            }
            els.append('1'); // inter-character narrow space
        }
        return els.toString();
    }

    static final String[] L = {"0001101", "0011001", "0010011", "0111101", "0100011",
            "0110001", "0101111", "0111011", "0110111", "0001011"};

    String ean13(String digits, boolean upc) throws Exception {
        String d = digits.replaceAll("[^0-9]", "");
        if (upc) {
            if (d.length() == 11) d += upcCheck(d);
            if (d.length() != 12) throw new Exception("UPC-A needs 11 or 12 digits");
            StringBuilder b = new StringBuilder("101");
            for (int i = 0; i < 6; i++) b.append(L[d.charAt(i) - '0']);
            b.append("01010");
            for (int i = 6; i < 12; i++) b.append(rCode(d.charAt(i) - '0'));
            b.append("101");
            return b.toString();
        }
        if (d.length() == 12) d += eanCheck(d);
        if (d.length() != 13) throw new Exception("EAN-13 needs 12 or 13 digits");
        String first = d.substring(0, 1);
        String rest = d.substring(1, 7);
        String[] parity = {"LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL"};
        String par = parity[Integer.parseInt(first)];
        StringBuilder b = new StringBuilder("101");
        for (int i = 0; i < 6; i++) {
            int dg = rest.charAt(i) - '0';
            b.append(par.charAt(i) == 'L' ? L[dg] : rev(L[dg]));
        }
        b.append("01010");
        for (int i = 7; i < 13; i++) b.append(rCode(d.charAt(i) - '0'));
        b.append("101");
        return b.toString();
    }

    String rCode(int d) {
        String l = L[d];
        StringBuilder sb = new StringBuilder();
        for (char c : l.toCharArray()) sb.append(c == '0' ? '1' : '0');
        return sb.toString();
    }

    String rev(String s) {
        return new StringBuilder(s).reverse().toString();
    }

    int eanCheck(String d12) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int v = d12.charAt(i) - '0';
            sum += (i % 2 == 0) ? v : v * 3;
        }
        return (10 - sum % 10) % 10;
    }

    int upcCheck(String d11) {
        int sum = 0;
        for (int i = 0; i < 11; i++) {
            int v = d11.charAt(i) - '0';
            sum += (i % 2 == 0) ? v * 3 : v;
        }
        return (10 - sum % 10) % 10;
    }

    static final String[] I25 = {"nnwwn", "wnnnw", "nwnnw", "wwnnn", "nnwnw", "wnwnn", "nwwnn", "nnnww", "wnnwn", "nwnwn"};

    String itf14(String digits) throws Exception {
        String d = digits.replaceAll("[^0-9]", "");
        if (d.length() == 13) {
            int sum = 0;
            for (int i = 0; i < 13; i++) {
                int v = d.charAt(i) - '0';
                sum += (i % 2 == 0) ? v * 3 : v;
            }
            d += (10 - sum % 10) % 10;
        }
        if (d.length() != 14) throw new Exception("ITF-14 needs 13 or 14 digits");
        StringBuilder b = new StringBuilder("1111"); // start: 4 narrow elements (b s b s)
        for (int i = 0; i < 13; i += 2) {
            String a = I25[d.charAt(i) - '0'];
            String c = I25[d.charAt(i + 1) - '0'];
            for (int j = 0; j < 5; j++) {
                b.append(a.charAt(j) == 'w' ? 2 : 1);
                b.append(c.charAt(j) == 'w' ? 2 : 1);
            }
        }
        b.append("211"); // end: wide bar, narrow space, narrow bar
        return b.toString();
    }

    // ---------- rendering view ----------
    class BarView extends View {
        boolean[][] qr;
        BarcodeSpec bc;
        Paint p = new Paint();

        BarView(Context c) {
            super(c);
        }

        void setQr(boolean[][] m) {
            qr = m;
            bc = null;
            invalidate();
        }

        void setBarcode(BarcodeSpec s) {
            bc = s;
            qr = null;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            cv.drawColor(Color.WHITE);
            p.setColor(Color.BLACK);
            if (qr != null) {
                int n = qr.length;
                float quiet = Math.min(w, h) * 0.08f;
                float size = Math.min(w, h) - quiet * 2;
                float cell = size / n;
                float ox = (w - size) / 2, oy = (h - size) / 2;
                for (int r = 0; r < n; r++) {
                    for (int c = 0; c < n; c++) {
                        if (qr[r][c]) {
                            cv.drawRect(ox + c * cell, oy + r * cell,
                                    ox + (c + 1) * cell + 1, oy + (r + 1) * cell + 1, p);
                        }
                    }
                }
            } else if (bc != null) {
                if (bc.isBits) {
                    // raw bit pattern
                    int n = bc.pattern.length();
                    float unit = (w - 20) / (float) n;
                    float x = 10;
                    for (int i = 0; i < n; i++) {
                        if (bc.pattern.charAt(i) == '1') {
                            cv.drawRect(x, h * 0.25f, x + unit + 1, h * 0.85f, p);
                        }
                        x += unit;
                    }
                } else {
                    // width pattern: alternating bar space, 1 narrow / 2 wide
                    float unit = (w - 20) / totalUnits(bc.pattern);
                    float x = 10;
                    for (int i = 0; i < bc.pattern.length(); i++) {
                        int width = bc.pattern.charAt(i) - '0';
                        if (i % 2 == 0) {
                            cv.drawRect(x, h * 0.25f, x + unit * width, h * 0.85f, p);
                        }
                        x += unit * width;
                    }
                }
                // human-readable text
                p.setColor(Ui.BG);
                p.setTextSize(h * 0.07f);
                String label = lastLabel == null ? "" : lastLabel;
                cv.drawText(label, w / 2f - p.measureText(label) / 2, h * 0.95f, p);
            }
        }

        float totalUnits(String pattern) {
            float t = 0;
            for (char c : pattern.toCharArray()) t += c - '0';
            return t;
        }

        Bitmap renderBitmap(int px) {
            if (qr == null && bc == null) return null;
            Bitmap bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            cv.drawColor(Color.WHITE);
            Paint pt = new Paint();
            pt.setColor(Color.BLACK);
            if (qr != null) {
                int n = qr.length;
                float quiet = px * 0.08f;
                float size = px - quiet * 2;
                float cell = size / n;
                for (int r = 0; r < n; r++) {
                    for (int c = 0; c < n; c++) {
                        if (qr[r][c]) {
                            cv.drawRect(quiet + c * cell, quiet + r * cell,
                                    quiet + (c + 1) * cell + 1, quiet + (r + 1) * cell + 1, pt);
                        }
                    }
                }
            } else if (bc != null) {
                if (bc.isBits) {
                    int n = bc.pattern.length();
                    float unit = (px - 40) / (float) n;
                    for (int i = 0; i < n; i++) {
                        if (bc.pattern.charAt(i) == '1') {
                            cv.drawRect(20 + i * unit, px * 0.2f, 20 + (i + 1) * unit, px * 0.8f, pt);
                        }
                    }
                } else {
                    float unit = (px - 40) / totalUnits(bc.pattern);
                    float x = 20;
                    for (int i = 0; i < bc.pattern.length(); i++) {
                        int width = bc.pattern.charAt(i) - '0';
                        if (i % 2 == 0) {
                            cv.drawRect(x, px * 0.2f, x + unit * width, px * 0.8f, pt);
                        }
                        x += unit * width;
                    }
                }
            }
            return bmp;
        }
    }
}
