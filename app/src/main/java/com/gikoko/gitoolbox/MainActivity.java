package com.gikoko.gitoolbox;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int FILE_REQ = 1001;
    private static final int CAM_REQ_FRONT = 2001;
    private static final int CAM_REQ_BACK = 2002;
    private ValueCallback<Uri[]> filePathCb;
    private WebView webView;
    private SensorManager sensorMgr;
    private final Map<String, float[]> sensorData = new HashMap<>();
    private final Map<Integer, Boolean> sensorActive = new HashMap<>();
    private AudioRecord audioRecord;
    private Thread micThread;
    private volatile double micAmp = 0;

    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent e) {
            String key = typeName(e.sensor.getType());
            if (key != null) sensorData.put(key, e.values.clone());
        }

        @Override
        public void onAccuracyChanged(Sensor s, int a) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        webView.setBackgroundColor(0xFF04150E);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePathCb != null) filePathCb.onReceiveValue(null);
                filePathCb = cb;
                try {
                    startActivityForResult(params.createIntent(), FILE_REQ);
                } catch (Exception e) {
                    filePathCb = null;
                    return false;
                }
                return true;
            }
        });
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String ua, String contentDisposition, String mime, long len) {
                if (url.startsWith("data:")) saveDataUrl(url);
                else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });
        webView.addJavascriptInterface(new NativeBridge(), "GI");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private String typeName(int t) {
        if (t == Sensor.TYPE_ACCELEROMETER) return "accel";
        if (t == Sensor.TYPE_GYROSCOPE) return "gyro";
        if (t == Sensor.TYPE_MAGNETIC_FIELD) return "mag";
        if (t == Sensor.TYPE_LIGHT) return "light";
        if (t == Sensor.TYPE_PROXIMITY) return "prox";
        if (t == Sensor.TYPE_PRESSURE) return "pressure";
        if (t == Sensor.TYPE_STEP_COUNTER) return "step";
        return null;
    }

    private int sensorTypeCode(String t) {
        if (t.equals("accel")) return Sensor.TYPE_ACCELEROMETER;
        if (t.equals("gyro")) return Sensor.TYPE_GYROSCOPE;
        if (t.equals("mag")) return Sensor.TYPE_MAGNETIC_FIELD;
        if (t.equals("light")) return Sensor.TYPE_LIGHT;
        if (t.equals("prox")) return Sensor.TYPE_PROXIMITY;
        if (t.equals("pressure")) return Sensor.TYPE_PRESSURE;
        if (t.equals("step")) return Sensor.TYPE_STEP_COUNTER;
        return -1;
    }

    private class NativeBridge {

        @JavascriptInterface
        public boolean android() {
            return true;
        }

        @JavascriptInterface
        public String version() {
            return "1.1";
        }

        @JavascriptInterface
        public boolean torch(int on) {
            try {
                CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
                String id = null;
                for (String c : cm.getCameraIdList()) {
                    CameraCharacteristics ch = cm.getCameraCharacteristics(c);
                    Boolean flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (flash != null && flash) {
                        id = c;
                        break;
                    }
                }
                if (id == null) return false;
                cm.setTorchMode(id, on == 1);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void vibrate(String pattern) {
            try {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                String[] parts = pattern.split(",");
                if (parts.length == 1) {
                    v.vibrate(VibrationEffect.createOneShot(Long.parseLong(parts[0].trim()), VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    long[] p = new long[parts.length];
                    for (int i = 0; i < parts.length; i++) p[i] = Long.parseLong(parts[i].trim());
                    v.vibrate(VibrationEffect.createWaveform(p, -1));
                }
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public String battery() {
            try {
                Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                JSONObject o = new JSONObject();
                int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                o.put("level", (level >= 0 && scale > 0) ? (level * 100 / scale) : -1);
                int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                o.put("charging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
                int temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                o.put("temp", temp >= 0 ? temp / 10.0 : -1);
                o.put("voltage", i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));
                int health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
                String hs = "Unknown";
                if (health == BatteryManager.BATTERY_HEALTH_GOOD) hs = "Good";
                else if (health == BatteryManager.BATTERY_HEALTH_OVERHEAT) hs = "Overheat";
                else if (health == BatteryManager.BATTERY_HEALTH_DEAD) hs = "Dead";
                else if (health == BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE) hs = "Over voltage";
                else if (health == BatteryManager.BATTERY_HEALTH_COLD) hs = "Cold";
                else if (health == BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE) hs = "Failure";
                o.put("health", hs);
                o.put("tech", i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY));
                o.put("plugged", i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0);
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String cpu() {
            try {
                JSONObject o = new JSONObject();
                o.put("cores", Runtime.getRuntime().availableProcessors());
                o.put("abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?");
                long freq = -1;
                try {
                    File f = new File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
                    if (f.exists()) {
                        java.io.FileInputStream in = new java.io.FileInputStream(f);
                        byte[] b = new byte[64];
                        int n = in.read(b);
                        in.close();
                        if (n > 0) freq = Long.parseLong(new String(b, 0, n).trim()) / 1000;
                    }
                } catch (Exception ignored) {
                }
                o.put("freq", freq);
                o.put("model", Build.HARDWARE);
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String ram() {
            try {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                JSONObject o = new JSONObject();
                o.put("total", mi.totalMem / 1048576L);
                o.put("avail", mi.availMem / 1048576L);
                o.put("low", mi.lowMemory);
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String device() {
            try {
                JSONObject o = new JSONObject();
                o.put("model", Build.MODEL);
                o.put("brand", Build.BRAND);
                o.put("manufacturer", Build.MANUFACTURER);
                o.put("device", Build.DEVICE);
                o.put("board", Build.BOARD);
                o.put("hardware", Build.HARDWARE);
                o.put("android", Build.VERSION.RELEASE);
                o.put("sdk", Build.VERSION.SDK_INT);
                o.put("security", Build.VERSION.SECURITY_PATCH);
                o.put("build", Build.ID);
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String sensors() {
            try {
                if (sensorMgr == null) sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
                List<Sensor> all = sensorMgr.getSensorList(Sensor.TYPE_ALL);
                JSONArray a = new JSONArray();
                for (Sensor s : all) {
                    JSONObject o = new JSONObject();
                    o.put("name", s.getName());
                    o.put("vendor", s.getVendor());
                    o.put("type", s.getType());
                    o.put("power", s.getPower());
                    a.put(o);
                }
                return a.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public String sensor(String type) {
            try {
                if (sensorMgr == null) sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
                int st = sensorTypeCode(type);
                if (st < 0) return "{}";
                Boolean act = sensorActive.get(st);
                if (act == null) {
                    Sensor s = sensorMgr.getDefaultSensor(st);
                    if (s == null) {
                        sensorActive.put(st, false);
                        return "{}";
                    }
                    sensorMgr.registerListener(sensorListener, s, SensorManager.SENSOR_DELAY_UI);
                    sensorActive.put(st, true);
                }
                float[] v = sensorData.get(type);
                JSONObject o = new JSONObject();
                if (v != null) {
                    JSONArray a = new JSONArray();
                    for (float x : v) a.put((double) x);
                    o.put("v", a);
                }
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String location() {
            try {
                JSONObject o = new JSONObject();
                if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION}, 3001);
                    o.put("status", "ask");
                    return o.toString();
                }
                LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
                Location best = null;
                for (String p : lm.getAllProviders()) {
                    try {
                        Location l = lm.getLastKnownLocation(p);
                        if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                    } catch (SecurityException ignored) {
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (best == null) {
                    o.put("status", "none");
                    return o.toString();
                }
                o.put("status", "ok");
                o.put("lat", best.getLatitude());
                o.put("lon", best.getLongitude());
                if (best.hasAltitude()) o.put("alt", best.getAltitude());
                o.put("spd", best.hasSpeed() ? best.getSpeed() : 0);
                o.put("acc", best.hasAccuracy() ? best.getAccuracy() : 0);
                o.put("time", best.getTime());
                return o.toString();
            } catch (Exception e) {
                return "{\"status\":\"error\"}";
            }
        }

        @JavascriptInterface
        public String micStart() {
            try {
                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 3002);
                    return "ask";
                }
                if (audioRecord != null) return "ok";
                int rate = 16000;
                int buf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, rate,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(buf, 4096));
                audioRecord.startRecording();
                final AudioRecord ar = audioRecord;
                micThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        short[] b = new short[2048];
                        while (ar == audioRecord && audioRecord != null
                                && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                            int n = audioRecord.read(b, 0, 2048);
                            if (n > 0) {
                                double sum = 0;
                                for (int i = 0; i < n; i++) sum += b[i] * (double) b[i];
                                micAmp = Math.sqrt(sum / n);
                            }
                        }
                    }
                });
                micThread.start();
                return "ok";
            } catch (Exception e) {
                return "error";
            }
        }

        @JavascriptInterface
        public double micLevel() {
            return micAmp;
        }

        @JavascriptInterface
        public void micStop() {
            try {
                if (audioRecord != null) {
                    audioRecord.stop();
                    audioRecord.release();
                }
            } catch (Exception ignored) {
            }
            audioRecord = null;
            micAmp = 0;
        }

        @JavascriptInterface
        public void shoot(int facing) {
            try {
                Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                i.putExtra("android.intent.extras.camera.facing", facing);
                i.putExtra("android.intent.extra.USE_FRONT_CAMERA", facing == 1);
                startActivityForResult(i, facing == 1 ? CAM_REQ_FRONT : CAM_REQ_BACK);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Camera unavailable", Toast.LENGTH_SHORT).show();
            }
        }

        @JavascriptInterface
        public void toast(String m) {
            try {
                Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {
            }
        }
    }

    private void saveDataUrl(String url) {
        try {
            String meta = url.substring(url.indexOf(',') + 1);
            byte[] data = Base64.decode(meta, Base64.DEFAULT);
            String ext = "png";
            if (url.startsWith("data:image/jpeg")) ext = "jpg";
            else if (url.startsWith("data:image/webp")) ext = "webp";
            else if (url.startsWith("data:application/pdf")) ext = "pdf";
            String name = "GIToolbox_" + System.currentTimeMillis() + "." + ext;
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(android.provider.MediaStore.Downloads.MIME_TYPE,
                        url.substring(5, url.indexOf(';')));
                Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                OutputStream os = getContentResolver().openOutputStream(uri);
                os.write(data);
                os.close();
                Toast.makeText(this, "Saved to Downloads: " + name, Toast.LENGTH_LONG).show();
            } else {
                File f = new File(getExternalFilesDir(null), name);
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(data);
                fos.close();
                Toast.makeText(this, "Saved: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_REQ && filePathCb != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
            filePathCb.onReceiveValue(results);
            filePathCb = null;
        } else if (requestCode == CAM_REQ_FRONT || requestCode == CAM_REQ_BACK) {
            try {
                Bitmap bm = (data != null && data.getExtras() != null) ? (Bitmap) data.getExtras().get("data") : null;
                if (bm != null) {
                    if (requestCode == CAM_REQ_FRONT) {
                        Matrix m = new Matrix();
                        m.preScale(-1, 1);
                        bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
                    }
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    bm.compress(Bitmap.CompressFormat.JPEG, 88, bo);
                    String url = "data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
                    webView.loadUrl("javascript:GI_onPhoto('" + url + "')");
                } else {
                    Toast.makeText(this, "Capture failed", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Capture failed", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (sensorMgr != null) sensorMgr.unregisterListener(sensorListener);
            sensorActive.clear();
        } catch (Exception ignored) {
        }
        try {
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                micAmp = 0;
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
