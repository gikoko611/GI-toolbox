package com.gikoko.gitoolbox;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * Icon3D v0.0.1 - procedurally drawn 3D tool icons.
 * Every tool gets a glossy gradient tile + a category-specific 3D shape with
 * a dark offset "depth" face and a specular highlight. No icon fonts - pure canvas.
 */
public final class Icon3D {

    private Icon3D() {
    }

    /** Neon gradient pairs used per tool (picked by name hash). */
    private static final int[][] PALETTE = {
            {0x3300E5FF, 0x00126E}, {0x3300FF9D, 0x00492A}, {0x33FF2E88, 0x560E2E},
            {0x33FFD700, 0x4E3A02}, {0x337C4DFF, 0x1B0A4E}, {0x3300B8FF, 0x053A5E},
            {0x33FF7A45, 0x4A1A02}, {0x3396FF45, 0x1A4A05},
    };

    public static Drawable tile(Context c, String tool, int cat) {
        int size = Ui.dp(c, 46);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        float s = size;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // per-tool gradient from name hash
        int h = Math.abs(tool.hashCode());
        int[] pair = PALETTE[h % PALETTE.length];

        // tile background
        RectF r = new RectF(0, 0, s, s);
        p.setShader(new LinearGradient(0, 0, s, s, pair[0], Color.parseColor("#05101E"),
                Shader.TileMode.CLAMP));
        cv.drawRoundRect(r, s * 0.22f, s * 0.22f, p);
        p.setShader(null);

        // 3D shape: dark depth face (offset +1.5,+2) then neon gradient face
        drawShape(cv, cat, s, 0x66000000, Color.parseColor("#02060E"), 1.5f, 2.0f);
        drawShape(cv, cat, s, pair[1] | 0xFF000000, pair[0] | 0xFF000000, 0, 0);

        // specular highlight (glass gloss, top-left arc)
        p.reset();
        p.setAntiAlias(true);
        p.setColor(0x59FFFFFF);
        Path gloss = new Path();
        gloss.addOval(new RectF(s * 0.12f, s * 0.08f, s * 0.55f, s * 0.30f), Path.Direction.CW);
        cv.drawPath(gloss, p);
        return new BitmapDrawable(c.getResources(), bmp);
    }

    /** Draw the category glyph centered; dark version offset for depth. */
    private static void drawShape(Canvas cv, int cat, float s, int dark, int light, float ox, float oy) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        float cx = s * 0.5f + ox, cy = s * 0.52f + oy;
        float u = s; // unit

        switch (cat) {
            case 0: { // Image: glossy picture frame + lens dot
                p.setColor(dark);
                cv.drawRoundRect(new RectF(cx - u * 0.28f, cy - u * 0.24f, cx + u * 0.28f, cy + u * 0.26f), u * 0.06f, u * 0.06f, p);
                p.setColor(light);
                p.setShader(new LinearGradient(cx - 20, cy - 20, cx + 20, cy + 20, light, light, Shader.TileMode.CLAMP));
                cv.drawRoundRect(new RectF(cx - u * 0.22f, cy - u * 0.18f, cx + u * 0.22f, cy + u * 0.20f), u * 0.05f, u * 0.05f, p);
                p.setShader(null);
                p.setColor(Color.WHITE);
                p.setAlpha(200);
                cv.drawCircle(cx, cy, u * 0.07f, p);
                p.setAlpha(255);
                break;
            }
            case 1: { // PDF: page with corner fold + text lines
                Path pg = new Path();
                pg.moveTo(cx - u * 0.18f, cy - u * 0.26f);
                pg.lineTo(cx + u * 0.10f, cy - u * 0.26f);
                pg.lineTo(cx + u * 0.20f, cy - u * 0.16f);
                pg.lineTo(cx + u * 0.20f, cy + u * 0.26f);
                pg.lineTo(cx - u * 0.18f, cy + u * 0.26f);
                pg.close();
                p.setColor(dark);
                cv.drawPath(pg, p);
                p.setColor(light);
                cv.drawPath(shift(pg, -1.5f), p);
                p.setColor(Color.WHITE);
                p.setAlpha(210);
                for (int i = 0; i < 3; i++) {
                    cv.drawRect(cx - u * 0.12f, cy - u * 0.10f + i * u * 0.11f, cx + u * 0.14f, cy - u * 0.05f + i * u * 0.11f, p);
                }
                p.setAlpha(255);
                break;
            }
            case 2: { // Text: bold type bars
                p.setColor(dark);
                cv.drawRect(cx - u * 0.24f, cy - u * 0.18f, cx + u * 0.24f, cy + u * 0.20f, p);
                p.setColor(light);
                cv.drawRect(cx - u * 0.25f, cy - u * 0.19f, cx + u * 0.23f, cy + u * 0.19f, p);
                p.setColor(Color.WHITE);
                p.setAlpha(220);
                cv.drawRect(cx - u * 0.16f, cy - u * 0.12f, cx + u * 0.16f, cy - u * 0.04f, p);
                cv.drawRect(cx - u * 0.16f, cy + u * 0.00f, cx + u * 0.10f, cy + u * 0.08f, p);
                cv.drawRect(cx - u * 0.16f, cy + u * 0.12f, cx + u * 0.13f, cy + u * 0.16f, p);
                p.setAlpha(255);
                break;
            }
            case 3: { // Converters: circular arrows
                p.setColor(dark);
                cv.drawCircle(cx, cy, u * 0.26f, p);
                p.setColor(light);
                cv.drawCircle(cx, cy, u * 0.25f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(u * 0.055f);
                p.setColor(Color.WHITE);
                p.setAlpha(230);
                Path arc = new Path();
                arc.addArc(new RectF(cx - u * 0.17f, cy - u * 0.17f, cx + u * 0.17f, cy + u * 0.17f), 300, 180);
                cv.drawPath(arc, p);
                p.setStyle(Paint.Style.FILL);
                // arrowheads
                cv.drawCircle(cx + u * 0.155f, cy - u * 0.05f, u * 0.045f, p);
                cv.drawCircle(cx - u * 0.155f, cy + u * 0.05f, u * 0.045f, p);
                p.setAlpha(255);
                break;
            }
            case 4: { // Calculators: 2x2 keys
                p.setColor(dark);
                cv.drawRoundRect(new RectF(cx - u * 0.26f, cy - u * 0.24f, cx + u * 0.26f, cy + u * 0.26f), u * 0.08f, u * 0.08f, p);
                p.setColor(light);
                cv.drawRoundRect(new RectF(cx - u * 0.27f, cy - u * 0.25f, cx + u * 0.25f, cy + u * 0.25f), u * 0.08f, u * 0.08f, p);
                p.setColor(Color.WHITE);
                p.setAlpha(210);
                float k = u * 0.09f, g = u * 0.115f;
                cv.drawCircle(cx - g, cy - g, k, p);
                cv.drawCircle(cx + g, cy - g, k, p);
                cv.drawCircle(cx - g, cy + g, k, p);
                cv.drawCircle(cx + g, cy + g, k, p);
                p.setAlpha(255);
                break;
            }
            case 5: { // Network: globe
                p.setColor(dark);
                cv.drawCircle(cx, cy, u * 0.26f, p);
                p.setColor(light);
                cv.drawCircle(cx, cy, u * 0.25f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(u * 0.035f);
                p.setColor(Color.WHITE);
                p.setAlpha(220);
                cv.drawCircle(cx, cy, u * 0.10f, p);
                cv.drawOval(new RectF(cx - u * 0.17f, cy - u * 0.25f, cx + u * 0.17f, cy + u * 0.25f), p);
                cv.drawLine(cx - u * 0.25f, cy, cx + u * 0.25f, cy, p);
                p.setAlpha(255);
                break;
            }
            case 6: { // QR: finder squares
                p.setColor(dark);
                cv.drawRoundRect(new RectF(cx - u * 0.27f, cy - u * 0.27f, cx + u * 0.27f, cy + u * 0.27f), u * 0.09f, u * 0.09f, p);
                p.setColor(light);
                cv.drawRoundRect(new RectF(cx - u * 0.28f, cy - u * 0.28f, cx + u * 0.26f, cy + u * 0.26f), u * 0.09f, u * 0.09f, p);
                p.setColor(Color.WHITE);
                p.setAlpha(230);
                float q = u * 0.12f, o = u * 0.15f;
                cv.drawRect(cx - o - q, cy - o - q, cx - o, cy - o, p);
                cv.drawRect(cx + o, cy - o - q, cx + o + q, cy - o, p);
                cv.drawRect(cx - o - q, cy + o, cx - o, cy + o + q, p);
                p.setAlpha(255);
                break;
            }
            case 7: { // Device: phone slab + screen
                p.setColor(dark);
                cv.drawRoundRect(new RectF(cx - u * 0.17f, cy - u * 0.28f, cx + u * 0.17f, cy + u * 0.28f), u * 0.05f, u * 0.05f, p);
                p.setColor(light);
                cv.drawRoundRect(new RectF(cx - u * 0.18f, cy - u * 0.29f, cx + u * 0.16f, cy + u * 0.27f), u * 0.05f, u * 0.05f, p);
                p.setColor(Color.WHITE);
                p.setAlpha(200);
                cv.drawRect(cx - u * 0.12f, cy - u * 0.20f, cx + u * 0.10f, cy + u * 0.16f, p);
                p.setAlpha(255);
                cv.drawCircle(cx - u * 0.01f, cy + u * 0.21f, u * 0.02f, p);
                break;
            }
            case 8: { // Security: shield
                Path sh = new Path();
                sh.moveTo(cx, cy - u * 0.28f);
                sh.lineTo(cx + u * 0.24f, cy - u * 0.18f);
                sh.lineTo(cx + u * 0.22f, cy + u * 0.06f);
                sh.quadTo(cx + u * 0.16f, cy + u * 0.22f, cx, cy + u * 0.30f);
                sh.quadTo(cx - u * 0.16f, cy + u * 0.22f, cx - u * 0.22f, cy + u * 0.06f);
                sh.lineTo(cx - u * 0.24f, cy - u * 0.18f);
                sh.close();
                p.setColor(dark);
                cv.drawPath(sh, p);
                p.setColor(light);
                cv.drawPath(shift(sh, -1.5f), p);
                p.setColor(Color.WHITE);
                p.setAlpha(230);
                Path check = new Path();
                check.moveTo(cx - u * 0.10f, cy);
                check.lineTo(cx - u * 0.02f, cy + u * 0.09f);
                check.lineTo(cx + u * 0.12f, cy - u * 0.09f);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(u * 0.045f);
                cv.drawPath(check, p);
                p.setStyle(Paint.Style.FILL);
                p.setAlpha(255);
                break;
            }
            case 9: { // Audio/Video: 3D sine wave + play dot
                p.setColor(dark);
                cv.drawRoundRect(new RectF(cx - u * 0.28f, cy - u * 0.17f, cx + u * 0.28f, cy + u * 0.19f), u * 0.10f, u * 0.10f, p);
                p.setColor(light);
                cv.drawRoundRect(new RectF(cx - u * 0.29f, cy - u * 0.18f, cx + u * 0.27f, cy + u * 0.18f), u * 0.10f, u * 0.10f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(u * 0.045f);
                p.setColor(Color.WHITE);
                p.setAlpha(235);
                Path wave = new Path();
                wave.moveTo(cx - u * 0.20f, cy);
                for (int i = 0; i <= 40; i++) {
                    float x = cx - u * 0.20f + (u * 0.40f * i) / 40f;
                    float y = cy - (float) Math.sin(i / 40f * Math.PI * 4) * u * 0.09f;
                    wave.lineTo(x, y);
                }
                cv.drawPath(wave, p);
                p.setStyle(Paint.Style.FILL);
                p.setAlpha(255);
                break;
            }
            case 10: { // Utility: 5-point star
                Path st = starPath(cx, cy, u * 0.28f, u * 0.12f, 5, -90);
                p.setColor(dark);
                cv.drawPath(st, p);
                p.setColor(light);
                cv.drawPath(shift(st, -1.5f), p);
                break;
            }
            case 11: { // AI: chip with pins
                p.setColor(dark);
                cv.drawRoundRect(new RectF(cx - u * 0.22f, cy - u * 0.22f, cx + u * 0.22f, cy + u * 0.22f), u * 0.05f, u * 0.05f, p);
                p.setColor(light);
                cv.drawRoundRect(new RectF(cx - u * 0.23f, cy - u * 0.23f, cx + u * 0.21f, cy + u * 0.21f), u * 0.05f, u * 0.05f, p);
                p.setColor(Color.WHITE);
                p.setAlpha(220);
                cv.drawRect(cx - u * 0.09f, cy - u * 0.09f, cx + u * 0.07f, cy + u * 0.07f, p);
                p.setStrokeWidth(u * 0.03f);
                for (int i = -1; i <= 1; i++) {
                    cv.drawRect(cx + i * u * 0.11f - u * 0.015f, cy - u * 0.30f, cx + i * u * 0.11f + u * 0.015f, cy - u * 0.23f, p);
                    cv.drawRect(cx + i * u * 0.11f - u * 0.015f, cy + u * 0.23f, cx + i * u * 0.11f + u * 0.015f, cy + u * 0.30f, p);
                }
                p.setAlpha(255);
                break;
            }
            case 12: { // System Pro: gear
                p.setColor(dark);
                cv.drawCircle(cx, cy, u * 0.27f, p);
                p.setColor(light);
                cv.drawCircle(cx, cy, u * 0.26f, p);
                p.setColor(Color.WHITE);
                p.setAlpha(225);
                for (int i = 0; i < 8; i++) {
                    double ang = Math.toRadians(i * 45);
                    float tx = cx + (float) Math.cos(ang) * u * 0.26f;
                    float ty = cy + (float) Math.sin(ang) * u * 0.26f;
                    cv.drawCircle(tx, ty, u * 0.055f, p);
                }
                p.setAlpha(255);
                p.setColor(Color.parseColor("#02060E"));
                cv.drawCircle(cx, cy, u * 0.09f, p);
                break;
            }
            case 13: { // WiFi & Comm: signal arcs + dot
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 3; i++) {
                    float rr = u * (0.12f + i * 0.09f);
                    p.setStrokeWidth(u * 0.055f);
                    p.setColor(dark);
                    p.setAlpha(255);
                    Path arc = new Path();
                    arc.addArc(new RectF(cx - rr, cy - rr * 1.2f - u * 0.10f, cx + rr, cy + rr * 0.8f - u * 0.10f), 205, 130);
                    cv.drawPath(arc, p);
                    p.setColor(light);
                    p.setAlpha(255);
                    Path arc2 = new Path();
                    arc2.addArc(new RectF(cx - rr - 1.5f, cy - rr * 1.2f - u * 0.115f, cx + rr - 1.5f, cy + rr * 0.8f - u * 0.115f), 205, 130);
                    cv.drawPath(arc2, p);
                }
                p.setStyle(Paint.Style.FILL);
                p.setColor(light);
                cv.drawCircle(cx, cy + u * 0.18f, u * 0.05f, p);
                break;
            }
            default: { // Fallback: 3D sphere
                p.setColor(dark);
                cv.drawCircle(cx, cy, u * 0.26f, p);
                p.setShader(new LinearGradient(cx - u * 0.2f, cy - u * 0.2f, cx + u * 0.2f, cy + u * 0.2f,
                        Color.WHITE, light, Shader.TileMode.CLAMP));
                cv.drawCircle(cx - 1.5f, cy - 1.5f, u * 0.25f, p);
                p.setShader(null);
            }
        }
    }

    private static Path shift(Path src, float d) {
        Path out = new Path(src);
        out.offset(d, d);
        return out;
    }

    private static Path starPath(float cx, float cy, float r1, float r2, int points, float startDeg) {
        Path p = new Path();
        for (int i = 0; i < points * 2; i++) {
            double ang = Math.toRadians(startDeg + i * (360f / (points * 2)));
            float r = i % 2 == 0 ? r1 : r2;
            float x = cx + (float) Math.cos(ang) * r;
            float y = cy + (float) Math.sin(ang) * r;
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.close();
        return p;
    }
}
