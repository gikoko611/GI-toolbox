package com.gikoko.gitoolbox;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Live sensor screens: list / accel / gyro / mag / compass / light / prox / altimeter. */
public class SensorActivity extends Activity implements SensorEventListener {

    private String tool;
    private SensorManager sm;
    private Sensor sensor;
    private TextView value, extra;
    private android.os.Handler handler;
    private int updates;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tool = getIntent().getStringExtra("tool");
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        handler = new android.os.Handler();

        LinearLayout root = Ui.screen(this);
        root.addView(Ui.header(this, tool, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));

        LinearLayout card = Ui.card(this);
        value = Ui.text(this, "Waiting for sensor data…", 15, Ui.GREEN, true);
        value.setTypeface(android.graphics.Typeface.MONOSPACE);
        card.addView(value);
        extra = Ui.text(this, "", 12, Ui.DIM, false);
        card.addView(extra);
        root.addView(card);

        Button btn = Ui.button(this, "⟳ RESET");
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updates = 0;
                Toast.makeText(SensorActivity.this, "Reset", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(btn);

        setContentView(Ui.scroll(this, root));
        registerSensor();
    }

    void registerSensor() {
        if (tool.equals("Sensor List")) {
            List<Sensor> all = sm.getSensorList(Sensor.TYPE_ALL);
            StringBuilder sb = new StringBuilder("SENSORS ON THIS DEVICE: " + all.size() + "\n\n");
            for (Sensor s : all) {
                sb.append("• ").append(s.getName()).append("\n  (")
                        .append(typeName(s.getType())).append(", power ")
                        .append(Ui.fmt(s.getPower(), 2)).append(" mA)\n");
            }
            value.setText(sb.toString());
            extra.setText("");
            return;
        }
        if (tool.equals("Accelerometer")) sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        else if (tool.equals("Gyroscope")) sensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        else if (tool.equals("Magnetometer")) sensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        else if (tool.equals("Light Sensor")) sensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT);
        else if (tool.equals("Proximity Sensor")) sensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        else sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER); // Compass + Altimeter use accel+mag
        if (sensor != null) {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
            if (tool.equals("Compass") || tool.equals("Altimeter")) {
                Sensor mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                if (mag != null) sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI);
            }
            if (tool.equals("Altimeter")) {
                Sensor pres = sm.getDefaultSensor(Sensor.TYPE_PRESSURE);
                if (pres != null) sm.registerListener(this, pres, SensorManager.SENSOR_DELAY_UI);
            }
            extra.setText("Sensor: " + sensor.getName());
        } else {
            value.setText("This device has no sensor for this tool.");
        }
    }

    String typeName(int t) {
        switch (t) {
            case Sensor.TYPE_ACCELEROMETER: return "Accelerometer";
            case Sensor.TYPE_GYROSCOPE: return "Gyroscope";
            case Sensor.TYPE_MAGNETIC_FIELD: return "Magnetometer";
            case Sensor.TYPE_LIGHT: return "Light";
            case Sensor.TYPE_PROXIMITY: return "Proximity";
            case Sensor.TYPE_PRESSURE: return "Pressure";
            case Sensor.TYPE_GRAVITY: return "Gravity";
            case Sensor.TYPE_LINEAR_ACCELERATION: return "Linear acceleration";
            case Sensor.TYPE_ROTATION_VECTOR: return "Rotation vector";
            case Sensor.TYPE_AMBIENT_TEMPERATURE: return "Temperature";
            case Sensor.TYPE_RELATIVE_HUMIDITY: return "Humidity";
            case Sensor.TYPE_STEP_COUNTER: return "Step counter";
            default: return "Type " + t;
        }
    }

    float[] gravity = new float[3];
    float[] geomag = new float[3];
    float pressure = -1;

    @Override
    public void onSensorChanged(SensorEvent e) {
        updates++;
        if (tool.equals("Sensor List")) return;
        if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity = e.values.clone();
            if (tool.equals("Accelerometer")) {
                float g = SensorManager.STANDARD_GRAVITY;
                float mag = (float) Math.sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]);
                value.setText("X: " + Ui.fmt(e.values[0], 2) + " m/s²\nY: " + Ui.fmt(e.values[1], 2) + " m/s²\nZ: " + Ui.fmt(e.values[2], 2) + " m/s²"
                        + "\n\nMagnitude: " + Ui.fmt(mag, 2) + "  (" + Ui.fmt(mag / g, 2) + " g)"
                        + "\nShake force: " + (mag / g > 1.4 ? "SHAKE DETECTED" : "stable"));
            }
            return;
        }
        if (e.sensor.getType() == Sensor.TYPE_GYROSCOPE && tool.equals("Gyroscope")) {
            value.setText("X: " + Ui.fmt(e.values[0], 3) + " rad/s\nY: " + Ui.fmt(e.values[1], 3) + " rad/s\nZ: " + Ui.fmt(e.values[2], 3) + " rad/s");
            return;
        }
        if (e.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomag = e.values.clone();
            if (tool.equals("Magnetometer")) {
                float mag = (float) Math.sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]);
                value.setText("X: " + Ui.fmt(e.values[0], 1) + " µT\nY: " + Ui.fmt(e.values[1], 1) + " µT\nZ: " + Ui.fmt(e.values[2], 1) + " µT"
                        + "\n\nMagnitude: " + Ui.fmt(mag, 1) + " µT");
            } else {
                updateCompass();
            }
            return;
        }
        if (e.sensor.getType() == Sensor.TYPE_LIGHT && tool.equals("Light Sensor")) {
            value.setText(Ui.fmt(e.values[0], 1) + " lx  (" + luxLabel(e.values[0]) + ")");
            return;
        }
        if (e.sensor.getType() == Sensor.TYPE_PROXIMITY && tool.equals("Proximity Sensor")) {
            value.setText(e.values[0] < 1 ? "◉ NEAR — object detected (" + Ui.fmt(e.values[0], 1) + " cm)" : "○ FAR (" + Ui.fmt(e.values[0], 1) + " cm)");
            return;
        }
        if (e.sensor.getType() == Sensor.TYPE_PRESSURE) {
            pressure = e.values[0];
            updateCompass();
        }
    }

    void updateCompass() {
        if (tool.equals("Compass")) {
            float[] r = new float[9];
            if (SensorManager.getRotationMatrix(r, null, gravity, geomag)) {
                float[] o = new float[3];
                SensorManager.getOrientation(r, o);
                float deg = (float) Math.toDegrees(o[0]);
                if (deg < 0) deg += 360;
                String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
                int idx = (int) (((deg + 22.5) % 360) / 45);
                value.setText("🧭 " + (int) deg + "°  " + dirs[idx]);
                extra.setText("Move the phone in a figure-8 to calibrate.");
            }
        } else if (tool.equals("Altimeter")) {
            if (pressure > 0) {
                float alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure);
                value.setText("⛰ Altitude: " + Ui.fmt(alt, 0) + " m\nPressure: " + Ui.fmt(pressure, 1) + " hPa");
                extra.setText("Barometric estimate (weather affects accuracy).");
            }
        }
    }

    String luxLabel(float l) {
        if (l < 10) return "very dark";
        if (l < 50) return "dark room";
        if (l < 500) return "indoor";
        if (l < 2000) return "bright indoor";
        if (l < 10000) return "overcast outdoor";
        return "direct sunlight";
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {
        // not used
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
        if (sensor != null && !tool.equals("Sensor List")) {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
            if (tool.equals("Compass") || tool.equals("Altimeter")) {
                Sensor mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                if (mag != null) sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI);
                Sensor pres = sm.getDefaultSensor(Sensor.TYPE_PRESSURE);
                if (tool.equals("Altimeter") && pres != null) sm.registerListener(this, pres, SensorManager.SENSOR_DELAY_UI);
            }
        }
    }
}
