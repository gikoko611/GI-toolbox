package com.gikoko.gitoolbox;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Ruler / Protractor / Bubble Level / Clinometer (custom drawn views). */
public class MeasureActivity extends Activity implements SensorEventListener {

    private String tool;
    private SensorManager sm;
    private TextView readout;
    private View dial;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tool = getIntent().getStringExtra("tool");
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.header(this, tool, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));

        readout = Ui.text(this, "", 15, Ui.GREEN, true);
        readout.setTypeface(android.graphics.Typeface.MONOSPACE);
        LinearLayout card = Ui.card(this);
        card.addView(readout);
        root.addView(card);

        if (tool.equals("Ruler")) {
            dial = new RulerView(this);
        } else if (tool.equals("Protractor")) {
            dial = new ProtractorView(this);
        } else {
            dial = new View(this); // level/clinometer use readout only
        }
        if (tool.equals("Ruler") || tool.equals("Protractor")) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 220));
            lp.topMargin = Ui.dp(this, 10);
            dial.setLayoutParams(lp);
            root.addView(dial);
        }

        TextView foot = Ui.text(this, "POWERED BY I KO KO", 10, Ui.GOLD, true);
        foot.setGravity(android.view.Gravity.CENTER);
        foot.setPadding(0, Ui.dp(this, 14), 0, 0);
        root.addView(foot);
        setContentView(Ui.scroll(this, root));

        if (tool.equals("Bubble Level") || tool.equals("Clinometer")) {
            Sensor acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (acc != null) sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI);
            readout.setText("Hold the phone flat…");
        } else {
            readout.setText(tool.equals("Ruler")
                    ? "Screen ruler. Align the edge of a real object with the lines. 1 line = 1 cm."
                    : "Tilt the phone or align the drawn baseline with the object you want to measure.");
        }
    }

    // -------- drawn views --------
    class RulerView extends View {
        Paint p = new Paint();

        RulerView(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float dp = getResources().getDisplayMetrics().density;
            int h = getHeight(), w = getWidth();
            cv.drawColor(Ui.BG2);
            p.setColor(Ui.GREEN);
            p.setStrokeWidth(Math.max(1, dp));
            float start = dp * 8;
            float pxPerCm = dp * 160f / 25.4f * 10f / getResources().getDisplayMetrics().density; // fallback
            pxPerCm = getResources().getDisplayMetrics().xdpi / 2.54f; // cm from real xdpi
            for (int cm = 0; cm < 30; cm++) {
                float y = start + cm * pxPerCm;
                if (y > h - dp * 4) break;
                p.setStrokeWidth(cm % 5 == 0 ? dp * 2 : dp);
                cv.drawLine(dp * 6, y, dp * 6 + (cm % 5 == 0 ? dp * 40 : dp * 24), y, p);
                if (cm % 5 == 0) {
                    p.setTextSize(dp * 10);
                    cv.drawText(String.valueOf(cm), dp * 50, y + dp * 3, p);
                }
            }
            p.setColor(Ui.DIM);
            p.setTextSize(dp * 9);
            cv.drawText("1 division = 1 cm (uses real screen density)", dp * 6, h - dp * 6, p);
        }
    }

    class ProtractorView extends View {
        Paint p = new Paint();

        ProtractorView(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float dp = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f, cy = getHeight() / 2f + dp * 20;
            float r = Math.min(getWidth(), getHeight()) / 2f - dp * 16;
            cv.drawColor(Ui.BG2);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(Ui.GREEN);
            p.setStrokeWidth(dp * 2);
            cv.drawCircle(cx, cy, r, p);
            p.setStrokeWidth(dp);
            for (int a = 0; a < 360; a += 10) {
                boolean major = a % 30 == 0;
                double rad = Math.toRadians(a - 90);
                float r2 = r - (major ? dp * 16 : dp * 8);
                cv.drawLine((float) (cx + Math.cos(rad) * r), (float) (cy + Math.sin(rad) * r),
                        (float) (cx + Math.cos(rad) * r2), (float) (cy + Math.sin(rad) * r2), p);
                if (a % 90 == 0) {
                    p.setStyle(Paint.Style.FILL);
                    p.setTextSize(dp * 11);
                    String label = (a == 0 ? "0°/360°" : a + "°");
                    float tx = (float) (cx + Math.cos(rad) * (r2 - dp * 12));
                    float ty = (float) (cy + Math.sin(rad) * (r2 - dp * 12));
                    cv.drawText(label, tx - p.measureText(label) / 2, ty, p);
                    p.setStyle(Paint.Style.STROKE);
                }
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.GOLD);
            cv.drawCircle(cx, cy, dp * 4, p);
            p.setColor(Ui.DIM);
            p.setTextSize(dp * 9);
            cv.drawText("For exact angle measurement use the Clinometer tool", dp * 8, getHeight() - dp * 6, p);
        }
    }

    // -------- level / clinometer via accelerometer --------
    float[] acc = new float[3];

    @Override
    public void onSensorChanged(SensorEvent e) {
        acc = e.values.clone();
        float pitch = (float) Math.toDegrees(Math.atan2(acc[0], Math.sqrt(acc[1] * acc[1] + acc[2] * acc[2])));
        float roll = (float) Math.toDegrees(Math.atan2(acc[1], Math.sqrt(acc[0] * acc[0] + acc[2] * acc[2])));
        if (tool.equals("Bubble Level")) {
            boolean flat = Math.abs(pitch) < 1.5 && Math.abs(roll) < 1.5;
            readout.setText("PITCH (X): " + Ui.fmt(pitch, 1) + "°\nROLL (Y):  " + Ui.fmt(roll, 1) + "°"
                    + "\n\n" + (flat ? "✅ LEVEL — flat within 1.5°" : "Tilt the phone toward level…"));
            readout.setTextColor(flat ? Ui.GREEN : Ui.GOLD);
        } else if (tool.equals("Clinometer")) {
            float angle = (float) Math.toDegrees(Math.atan2(Math.abs(acc[1]), Math.abs(acc[2])));
            readout.setText("ANGLE: " + Ui.fmt(angle, 1) + "°\n\nRest the phone's edge on the slope surface to read its incline."
                    + "\nPitch: " + Ui.fmt(pitch, 1) + "°  Roll: " + Ui.fmt(roll, 1) + "°");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor s, int a) {
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            sm.unregisterListener(this);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tool.equals("Bubble Level") || tool.equals("Clinometer")) {
            Sensor a = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (a != null) sm.registerListener(this, a, SensorManager.SENSOR_DELAY_UI);
        }
    }
}
