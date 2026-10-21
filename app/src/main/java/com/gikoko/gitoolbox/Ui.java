package com.gikoko.gitoolbox;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Ui v3.0 - "Nebula Green" theme engine: gradient surfaces, 3D elevation shadows,
 * ripple touches, entrance animations, Padauk typography. 100% programmatic.
 */
public final class Ui {

    public static final int BG = Color.parseColor("#04150E");
    public static final int BG2 = Color.parseColor("#0A241A");
    public static final int CARD = Color.parseColor("#0B2117");
    public static final int GREEN = Color.parseColor("#00FF9D");
    public static final int GOLD = Color.parseColor("#FFD700");
    public static final int TEXT = Color.parseColor("#EAFFF4");
    public static final int DIM = Color.parseColor("#3F8A6B");
    public static final int EDGE = Color.parseColor("#1F5540");
    public static final int RED = Color.parseColor("#FF6B6B");

    private Ui() {
    }

    // ---------------------------------------------------------------- fonts

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
        t.setTypeface(bold ? (FONT_BOLD != null ? FONT_BOLD : Typeface.DEFAULT_BOLD)
                : (FONT != null ? FONT : Typeface.DEFAULT));
    }

    private static Typeface fontFor(boolean bold, Context c) {
        ensureFonts(c);
        if (bold) return FONT_BOLD != null ? FONT_BOLD : Typeface.DEFAULT_BOLD;
        return FONT != null ? FONT : Typeface.DEFAULT;
    }

    // ---------------------------------------------------------------- core

    public static int dp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    /** Rounded solid surface. */
    public static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, (int) radiusDp));
        return g;
    }

    /** Rounded surface with hairline stroke. */
    public static GradientDrawable roundStroke(int color, int stroke, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, (int) radiusDp));
        g.setStroke(Math.max(1, dp(c, 1)), stroke);
        return g;
    }

    /** Vertical gradient surface with hairline stroke - the v3 card look. */
    public static GradientDrawable surface(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setOrientation(GradientDrawable.Orientation.TL_BR);
        g.setColors(new int[]{Color.parseColor("#0D2A1D"), Color.parseColor("#081C13")});
        g.setCornerRadius(dp(c, 16));
        g.setStroke(Math.max(1, dp(c, 1)), EDGE);
        return g;
    }

    /** Glassy gradient button surface with gold/green stroke. */
    public static GradientDrawable buttonSurface(Context c, int accent) {
        GradientDrawable g = new GradientDrawable();
        g.setOrientation(GradientDrawable.Orientation.TL_BR);
        g.setColors(new int[]{Color.parseColor("#103526"), Color.parseColor("#071F15")});
        g.setCornerRadius(dp(c, 12));
        g.setStroke(Math.max(1, dp(c, 1)),
                accent == GOLD ? Color.parseColor("#8A7420") : Color.parseColor("#1F6C4E"));
        return g;
    }

    private static RippleDrawable ripple(int color, android.graphics.drawable.Drawable bg) {
        return new RippleDrawable(ColorStateList.valueOf(color), bg, bg);
    }

    // ---------------------------------------------------------------- widgets

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
        t.setLetterSpacing(0.14f);
        return t;
    }

    public static Button button(Context c, String label, int color) {
        Button b = new Button(c, null, 0);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(color);
        b.setTypeface(fontFor(true, c));
        b.setTextSize(13);
        b.setLetterSpacing(0.06f);
        b.setBackground(ripple(0x3300FF9D, buttonSurface(c, color)));
        b.setElevation(dp(c, 4));
        b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        return b;
    }

    public static Button button(Context c, String label) {
        return button(c, label, GREEN);
    }

    public static EditText input(Context c, String hint, boolean multiline) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(DIM);
        e.setTextColor(TEXT);
        e.setTextSize(14);
        e.setTypeface(fontFor(false, c));
        e.setBackground(ripple(0x1A00FF9D, roundStroke(Color.parseColor("#071B12"),
                Color.parseColor("#265F47"), 12, c)));
        e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
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
        l.setBackground(surface(c));
        l.setElevation(dp(c, 5));
        int p = dp(c, 14);
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
        s.setVerticalFadingEdgeEnabled(true);
        s.setFadingEdgeLength(dp(c, 24));
        if (child instanceof LinearLayout) ((LinearLayout) child).setOrientation(LinearLayout.VERTICAL);
        s.addView(child);
        return s;
    }

    public static LinearLayout screen(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        g.setColors(new int[]{Color.parseColor("#062018"), Color.parseColor("#04150E"), Color.parseColor("#020C08")});
        root.setBackground(g);
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

    // ---------------------------------------------------------------- v3 extras

    /** Small status pill, e.g. "● LIVE". */
    public static TextView badge(Context c, String s, int color) {
        TextView t = text(c, s, 10, color, true);
        t.setLetterSpacing(0.12f);
        t.setBackground(roundStroke(Color.parseColor("#06140D"), color, 99, c));
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        return t;
    }

    /** Entrance reveal: fade + rise. delayMs staggers lists. */
    public static void reveal(View v, long delayMs) {
        AnimationSet set = new AnimationSet(true);
        AlphaAnimation a = new AlphaAnimation(0f, 1f);
        a.setDuration(360);
        TranslateAnimation tr = new TranslateAnimation(0, 0, dp(v.getContext(), 26), 0);
        tr.setDuration(380);
        set.addAnimation(a);
        set.addAnimation(tr);
        set.setStartOffset(delayMs);
        v.startAnimation(set);
    }

    public static void reveal(View v) {
        reveal(v, 0);
    }

    /** Pop-in with a slight overshoot (splash, logos, results). */
    public static void pop(View v) {
        ScaleAnimation s = new ScaleAnimation(0.86f, 1f, 0.86f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        s.setDuration(420);
        v.startAnimation(s);
    }

    /** Gentle infinite breathing glow (icon pulses). */
    public static void breathe(View v) {
        ScaleAnimation s = new ScaleAnimation(1f, 1.08f, 1f, 1.08f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        s.setDuration(1200);
        s.setRepeatCount(Animation.INFINITE);
        s.setRepeatMode(Animation.REVERSE);
        v.startAnimation(s);
    }

    /** Horizontal thin divider line. */
    public static View divider(Context c) {
        View d = new View(c);
        d.setBackground(new ColorDrawable(0x1620975A));
        d.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1))));
        return d;
    }

    // ---------------------------------------------------------------- format

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
