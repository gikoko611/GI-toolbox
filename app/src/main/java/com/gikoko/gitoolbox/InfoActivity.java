package com.gikoko.gitoolbox;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;

/** Device / CPU / RAM / Storage / Battery info screens. */
public class InfoActivity extends Activity {

    private String tool;

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
        TextView out = Ui.text(this, "", 13, Ui.TEXT, false);
        LinearLayout card = Ui.card(this);
        card.addView(out);
        root.addView(card);
        Button refresh = Ui.button(this, "⟳ REFRESH");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(InfoActivity.this, "Refreshing…", Toast.LENGTH_SHORT).show();
                recreate();
            }
        });
        root.addView(refresh);
        if (tool.equals("Screen Test") || tool.equals("Dead Pixel Test")) {
            Button color = Ui.button(this, "⛶ FULL SCREEN COLOR TEST", Ui.GOLD);
            color.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startColorTest();
                }
            });
            root.addView(color);
        }
        setContentView(Ui.scroll(this, root));
        out.setText(build());
    }

    void startColorTest() {
        final View v = new View(this);
        final int[] colors = {0xFF000000, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF, 0xFF04150E};
        final int[] idx = {0};
        v.setBackgroundColor(colors[0]);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                idx[0] = (idx[0] + 1) % colors.length;
                v.setBackgroundColor(colors[idx[0]]);
            }
        });
        setContentView(v);
    }

    String build() {
        if (tool.equals("Device Info")) return deviceInfo();
        if (tool.equals("CPU Monitor")) return cpuInfo();
        if (tool.equals("RAM Monitor")) return ramInfo();
        if (tool.equals("Storage Info")) return storageInfo();
        if (tool.equals("Screen Test") || tool.equals("Dead Pixel Test")) return screenInfo();
        return batteryInfo();
    }

    String deviceInfo() {
        return "MODEL:        " + Build.MODEL
                + "\nBRAND:        " + Build.BRAND
                + "\nMANUFACTURER: " + Build.MANUFACTURER
                + "\nDEVICE:       " + Build.DEVICE
                + "\nPRODUCT:      " + Build.PRODUCT
                + "\nBOARD:        " + Build.BOARD
                + "\nHARDWARE:     " + Build.HARDWARE
                + "\nANDROID:      " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\nSECURITY PATCH: " + Build.VERSION.SECURITY_PATCH
                + "\nBUILD ID:     " + Build.DISPLAY
                + "\n\nABI:         " + (Build.SUPPORTED_ABIS == null ? "?" : TextUtils.join(", ", Build.SUPPORTED_ABIS))
                + "\n\nSCREEN:       " + getResources().getDisplayMetrics().widthPixels + " x "
                + getResources().getDisplayMetrics().heightPixels
                + "\nDENSITY:      " + getResources().getDisplayMetrics().densityDpi + " dpi"
                + "\nREFRESH:      ~" + (getResources().getDisplayMetrics().density * 160 / getResources().getDisplayMetrics().density) + " Hz (est)"
                + "\nLOCALE:       " + Locale.getDefault().toLanguageTag()
                + "\nTIMEZONE:     " + java.util.TimeZone.getDefault().getID()
                + "\n\nKERNEL:       " + System.getProperty("os.version")
                + "\nJAVA HEAP:     max " + Compute.fmtBytes(Runtime.getRuntime().maxMemory());
    }

    String cpuInfo() {
        int cores = Runtime.getRuntime().availableProcessors();
        long freq = 0;
        String governor = null;
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"));
            String l = r.readLine();
            r.close();
            if (l != null) freq = Long.parseLong(l.trim()) * 1000L;
        } catch (Exception ignored) {
        }
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"));
            governor = r.readLine();
            r.close();
        } catch (Exception ignored) {
        }
        double hz = freq / 1e9;
        return "CPU CORES:    " + cores
                + (freq > 0 ? "\nMAX FREQUENCY: " + Ui.fmt(hz, 2) + " GHz (" + freq / 1000000L + " MHz)" : "")
                + "\nGOVERNOR:     " + (governor == null ? "unknown" : governor)
                + "\n\nUSAGE ESTIMATE: " + Ui.fmt(cpuUsage(), 1) + "%"
                + "\n\nNote: live per-core usage needs the next native module. This shows core count, frequency and an activity estimate.\nRefresh to update.";
    }

    double cpuUsage() {
        try {
            long[] c1 = readCpu();
            Thread.sleep(300);
            long[] c2 = readCpu();
            long idle1 = c1[3], total1 = 0;
            for (long x : c1) total1 += x;
            long idle2 = c2[3], total2 = 0;
            for (long x : c2) total2 += x;
            long dt = total2 - total1, di = idle2 - idle1;
            return dt <= 0 ? 0 : Math.min(100, Math.max(0, (dt - di) * 100.0 / dt));
        } catch (Exception e) {
            return 0;
        }
    }

    long[] readCpu() throws Exception {
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/proc/stat"));
        String line = r.readLine();
        r.close();
        String[] p = line.split("\\s+");
        long[] v = new long[8];
        for (int i = 0; i < 8 && i < p.length - 1; i++) v[i] = Long.parseLong(p[i + 1]);
        return v;
    }

    String ramInfo() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        am.getMemoryInfo(mi);
        double used = mi.totalMem - mi.availMem;
        return "TOTAL RAM:  " + Compute.fmtBytes(mi.totalMem)
                + "\nUSED:       " + Compute.fmtBytes(used) + "  (" + Ui.fmt(used * 100.0 / mi.totalMem, 1) + "%)"
                + "\nAVAILABLE:  " + Compute.fmtBytes(mi.availMem)
                + "\nTHRESHOLD:  " + Compute.fmtBytes(mi.threshold)
                + "\nLOW MEMORY: " + (mi.lowMemory ? "YES" : "NO")
                + "\n\nAPP HEAP:   max " + Compute.fmtBytes(Runtime.getRuntime().maxMemory())
                + "\n            used " + Compute.fmtBytes(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
                + "\nRefresh to update.";
    }

    String storageInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("INTERNAL STORAGE\n");
        appendStat(sb, Environment.getDataDirectory());
        sb.append("\nSD CARD\n");
        String sd = System.getenv("SECONDARY_STORAGE");
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            appendStat(sb, Environment.getExternalStorageDirectory());
        } else {
            sb.append("State: " + Environment.getExternalStorageState());
        }
        sb.append("\nAPP FILES DIR\n");
        File f = getFilesDir();
        sb.append("Path: " + f.getAbsolutePath() + "\nFree: " + Compute.fmtBytes(f.getFreeSpace()));
        sb.append("\n\nRefresh to update.");
        return sb.toString();
    }

    void appendStat(StringBuilder sb, File dir) {
        try {
            StatFs st = new StatFs(dir.getAbsolutePath());
            sb.append("Total:  " + Compute.fmtBytes(st.getTotalBytes())
                    + "\nFree:   " + Compute.fmtBytes(st.getAvailableBytes())
                    + "\nUsed:   " + Compute.fmtBytes(st.getTotalBytes() - st.getAvailableBytes())
                    + "\n");
        } catch (Exception e) {
            sb.append("Unavailable\n");
        }
    }

    String screenInfo() {
        return "WIDTH:   " + getResources().getDisplayMetrics().widthPixels + " px"
                + "\nHEIGHT:  " + getResources().getDisplayMetrics().heightPixels + " px"
                + "\nDENSITY: " + getResources().getDisplayMetrics().densityDpi + " dpi  (x" + Ui.fmt(getResources().getDisplayMetrics().density) + ")"
                + "\n\nTap FULL SCREEN COLOR TEST below to cycle colors (black, red, green, blue, white). Tap the screen to change color; use BACK to exit.";
    }

    String batteryInfo() {
        BatteryManager bm = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        Integer cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        Integer curr = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        StringBuilder sb = new StringBuilder();
        sb.append("LEVEL:     ").append(cap == Integer.MIN_VALUE ? "?" : cap).append("%")
                .append("\nCHARGING:  ").append(bm.isCharging() ? "YES" : "NO");
        sb.append("\n\nNote: detailed charge counter / health APIs vary by device (Redmi 9A reports limited data).")
                .append("\n\nCharge counter: ").append(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) / 1000).append(" mAh (if reported)");
        if (curr != null && curr != Integer.MIN_VALUE) {
            sb.append("\nCurrent: ").append(Ui.fmt(Math.abs(curr) / 1000.0, 0)).append(" mA ").append(curr < 0 ? "(discharging)" : "(charging)");
        }
        return sb.toString();
    }
}
