package com.gikoko.gitoolbox;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Cyber theme helpers: neon green #00ff9d / gold #ffd700 / dark #04150e */
public final class Ui {

    public static final int BG = Color.parseColor("#04150E");
    public static final int BG2 = Color.parseColor("#0A241A");
    public static final int CARD = Color.parseColor("#0B2117");
    public static final int GREEN = Color.parseColor("#00FF9D");
    public static final int GOLD = Color.parseColor("#FFD700");
    public static final int TEXT = Color.parseColor("#EAFFF4");
    public static final int DIM = Color.parseColor("#3F8A6B");

    private Ui() {
    }

    // ---------- Padauk font (bundled Burmese font, OFL) ----------

    private static Typeface FONT;
    private static Typeface FONT_BOLD;
    private static boolean fontTried = false;

    private static void ensureFonts(Context c) {
        if (fontTried) return;
        fontTried = true;
        try {
            FONT = Typeface.createFromAsset(c.getAssets(), "fonts/padauk_regular.ttf");
        } catch (Exception e) {
            FONT = null;
        }
        try {
            FONT_BOLD = Typeface.createFromAsset(c.getAssets(), "fonts/padauk_bold.ttf");
        } catch (Exception e) {
            FONT_BOLD = null;
        }
    }

    /** Apply the bundled Burmese font to a view built outside Ui helpers. */
    public static void applyTypeface(TextView t, boolean bold, Context c) {
        ensureFonts(c);
        if (bold) {
            t.setTypeface(FONT_BOLD != null ? FONT_BOLD : Typeface.DEFAULT_BOLD);
        } else {
            t.setTypeface(FONT != null ? FONT : Typeface.DEFAULT);
        }
    }

    private static Typeface fontFor(boolean bold, Context c) {
        ensureFonts(c);
        if (bold) return FONT_BOLD != null ? FONT_BOLD : Typeface.DEFAULT_BOLD;
        return FONT != null ? FONT : Typeface.DEFAULT;
    }

    public static int dp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, (int) radiusDp));
        return g;
    }

    public static GradientDrawable roundStroke(int color, int stroke, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, (int) radiusDp));
        g.setStroke(Math.max(1, dp(c, 1)), stroke);
        return g;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(fontFor(bold, c));
        return t;
    }

    public static TextView gold(Context c, String s, float sp) {
        TextView t = text(c, s, sp, GOLD, true);
        t.setLetterSpacing(0.18f);
        return t;
    }

    public static Button button(Context c, String label, int color) {
        Button b = new Button(c, null, 0);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(color);
        b.setTypeface(fontFor(true, c));
        b.setTextSize(13);
        b.setBackground(roundStroke(Color.parseColor("#0A2218"), Color.parseColor("#235A44"), 10, c));
        b.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        return b;
    }

    public static Button button(Context c, String label) {
        return button(c, label, GREEN);
    }

    public static EditText input(Context c, String hint, boolean multiline) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(DIM);
        e.setTypeface(fontFor(false, c));
        e.setTextColor(TEXT);
        e.setTextSize(14);
        e.setBackground(roundStroke(Color.parseColor("#071B12"), Color.parseColor("#1E4B38"), 10, c));
        e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        e.setSingleLine(!multiline);
        if (multiline) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            e.setMinLines(4);
            e.setGravity(Gravity.TOP);
        }
        return e;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(CARD, 12, c));
        int p = dp(c, 12);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    public static ScrollView scroll(Context c, View child) {
        ScrollView s = new ScrollView(c);
        s.setFillViewport(true);
        if (child instanceof LinearLayout) ((LinearLayout) child).setOrientation(LinearLayout.VERTICAL);
        s.addView(child);
        return s;
    }

    public static LinearLayout screen(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        int p = dp(c, 14);
        root.setPadding(p, dp(c, 16), p, p);
        return root;
    }

    public static LinearLayout header(Context c, String title, final Runnable back) {
        LinearLayout h = row(c);
        h.setGravity(Gravity.CENTER_VERTICAL);
        Button b = button(c, "←", GREEN);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                back.run();
            }
        });
        h.addView(b);
        TextView t = gold(c, title, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(c, 10);
        t.setLayoutParams(lp);
        h.addView(t);
        return h;
    }

    public static String fmt(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e12) {
            return String.valueOf((long) d);
        }
        return String.format("%.4f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public static String fmt(double d, int places) {
        return String.format("%." + places + "f", d);
    }
}
