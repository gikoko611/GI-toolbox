package com.gikoko.gitoolbox;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * v3.0 "System Pro" suite — 3C All-in-One Toolbox style system manager:
 * app manager, APK extractor, uninstaller, task manager, RAM boost, cache cleaner,
 * permission / startup / data usage / network monitoring, volume + sound profiles,
 * clipboard manager, and a dozen system inspectors. 100% native, no root required.
 */
public class SysActivity extends Activity {

    private String tool;
    private LinearLayout root;
    private LinearLayout out;
    private Handler h = new Handler();
    private boolean keepOn = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tool = getIntent().getStringExtra("tool");
        root = Ui.screen(this);
        root.addView(Ui.header(this, tool, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));
        LinearLayout br = Ui.row(this);
        br.addView(Ui.badge(this, "● SYSTEM PRO", Ui.GREEN));
        root.addView(br);
        Ui.reveal(br);

        out = new LinearLayout(this);
        out.setOrientation(LinearLayout.VERTICAL);
        root.addView(out);

        setContentView(Ui.scroll(this, root));
        dispatch();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacksAndMessages(null);
    }

    void toast(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(SysActivity.this, s, Toast.LENGTH_SHORT).show();
            }
        });
    }

    LinearLayout card(String title, String sub, int titleColor) {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, title, 13, titleColor, true));
        if (sub != null) c.addView(Ui.text(this, sub, 11, Ui.DIM, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        c.setLayoutParams(lp);
        return c;
    }

    void dispatch() {
        if (tool.equals("App Manager")) appList("manager");
        else if (tool.equals("APK Extractor")) appList("extract");
        else if (tool.equals("App Uninstaller")) appList("uninstall");
        else if (tool.equals("App Sizes")) appList("sizes");
        else if (tool.equals("Exported Checker")) appList("exported");
        else if (tool.equals("Task Manager")) taskManager();
        else if (tool.equals("RAM Boost")) ramBoost();
        else if (tool.equals("Cache Cleaner")) cacheCleaner();
        else if (tool.equals("Permission Manager")) permissionManager();
        else if (tool.equals("Startup Manager")) startupManager();
        else if (tool.equals("Data Usage")) dataUsage();
        else if (tool.equals("Network Monitor")) networkMonitor();
        else if (tool.equals("Clipboard Manager")) clipboardManager();
        else if (tool.equals("Keep Screen On")) keepScreenOn();
        else if (tool.equals("Volume Manager")) volumeManager();
        else if (tool.equals("Sound Profile")) soundProfile();
        else if (tool.equals("Ringtone Picker")) ringtonePicker();
        else if (tool.equals("Refresh Rate")) refreshRate();
        else if (tool.equals("WebView Version")) webViewVersion();
        else if (tool.equals("Keyboard List")) keyboardList();
        else if (tool.equals("TTS Engines")) ttsEngines();
        else if (tool.equals("Accessibility Checker")) accessibilityChecker();
        else if (tool.equals("Uptime")) uptime();
        else if (tool.equals("Kernel Info")) kernelInfo();
        else if (tool.equals("Storage Volumes")) storageVolumes();
        else infoCard(tool, "Module loading…");
    }

    // ---------------------------------------------------------------- app lists

    void appList(final String mode) {
        final TextView status = Ui.text(this, "Scanning installed apps…", 12, Ui.GOLD, true);
        out.addView(status);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<PackageInfo> pkgs = new ArrayList<>();
                try {
                    int flags = PackageManager.GET_ACTIVITIES;
                    pkgs.addAll(getPackageManager().getInstalledPackages(flags));
                } catch (Exception e) {
                    try {
                        pkgs.addAll(getPackageManager().getInstalledPackages(0));
                    } catch (Exception ignored) {
                    }
                }
                List<PackageInfo> sorted = new ArrayList<>(pkgs);
                if (mode.equals("sizes")) {
                    try {
                        Collections.sort(sorted, new Comparator<PackageInfo>() {
                            @Override
                            public int compare(PackageInfo a, PackageInfo b) {
                                return Long.compare(apkSize(b), apkSize(a));
                            }
                        });
                    } catch (Exception ignored) {
                    }
                } else {
                    final PackageManager pm = getPackageManager();
                    Collections.sort(sorted, new Comparator<PackageInfo>() {
                        @Override
                        public int compare(PackageInfo a, PackageInfo b) {
                            return String.CASE_INSENSITIVE_ORDER.compare(label(a, pm), label(b, pm));
                        }
                    });
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        status.setText(pkgs.size() + " apps installed · " + modeLabel(mode));
                        renderApps(sorted, mode);
                    }
                });
            }
        }).start();
    }

    String modeLabel(String m) {
        if (m.equals("manager")) return "tap buttons to manage each app";
        if (m.equals("extract")) return "backup any APK to Downloads";
        if (m.equals("uninstall")) return "tap UNINSTALL, then confirm";
        if (m.equals("sizes")) return "largest APKs first";
        return "exported activity audit";
    }

    String label(PackageInfo p, PackageManager pm) {
        try {
            if (p.applicationInfo != null) {
                CharSequence c = pm.getApplicationLabel(p.applicationInfo);
                if (c != null) return c.toString();
            }
        } catch (Exception ignored) {
        }
        return p.packageName;
    }

    long apkSize(PackageInfo p) {
        try {
            if (p.applicationInfo != null && p.applicationInfo.sourceDir != null) {
                return new File(p.applicationInfo.sourceDir).length();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    int exportedCount(PackageInfo p) {
        int n = 0;
        try {
            if (p.activities != null) {
                for (int i = 0; i < p.activities.length; i++) {
                    if (p.activities[i].exported) n++;
                }
            }
        } catch (Exception ignored) {
        }
        return n;
    }

    void renderApps(final List<PackageInfo> pkgs, String mode) {
        int rendered = 0;
        for (int i = 0; i < pkgs.size(); i++) {
            final PackageInfo p = pkgs.get(i);
            final PackageManager pm = getPackageManager();
            final String name = label(p, pm);
            final boolean system = (p.applicationInfo != null
                    && (p.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            String sub = p.packageName
                    + (system ? " · SYSTEM" : " · USER")
                    + (p.versionName != null ? " · v" + p.versionName : "");
            if (mode.equals("sizes")) {
                sub += "\nAPK: " + Compute.fmtBytes(apkSize(p));
            } else if (mode.equals("exported")) {
                sub += "\nEXPORTED ACTIVITIES: " + exportedCount(p);
            } else {
                sub += "\nAPK: " + Compute.fmtBytes(apkSize(p));
            }
            final LinearLayout c = card(name, sub, system ? Ui.DIM : Ui.TEXT);
            if (mode.equals("manager")) {
                LinearLayout r1 = Ui.row(this);
                r1.addView(weightBtn(openBtn(p.packageName)));
                Button inf = Ui.button(this, "⚙ INFO", Ui.GREEN);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                ilp.leftMargin = Ui.dp(this, 6);
                inf.setLayoutParams(ilp);
                inf.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        appSettings(p.packageName);
                    }
                });
                r1.addView(inf);
                c.addView(r1);
                LinearLayout r2 = Ui.row(this);
                r2.setPadding(0, Ui.dp(this, 6), 0, 0);
                r2.addView(weightBtn(apkBtn(name, p)));
                Button del = Ui.button(this, "🗑 DEL", Ui.GOLD);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                dlp.leftMargin = Ui.dp(this, 6);
                del.setLayoutParams(dlp);
                del.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            startActivity(new Intent(Intent.ACTION_DELETE,
                                    Uri.parse("package:" + p.packageName)));
                        } catch (Exception e) {
                            toast("Cannot open uninstaller");
                        }
                    }
                });
                r2.addView(del);
                c.addView(r2);
            } else if (mode.equals("extract")) {
                Button ex = Ui.button(this, "📦 EXTRACT APK", Ui.GREEN);
                ex.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        extractApk(name, p);
                    }
                });
                c.addView(ex);
            } else if (mode.equals("uninstall")) {
                Button del = Ui.button(this, "🗑 UNINSTALL", Ui.GOLD);
                del.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            startActivity(new Intent(Intent.ACTION_DELETE,
                                    Uri.parse("package:" + p.packageName)));
                        } catch (Exception e) {
                            toast("Cannot open uninstaller");
                        }
                    }
                });
                c.addView(del);
            } else if (mode.equals("exported")) {
                c.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        appSettings(p.packageName);
                    }
                });
            }
            out.addView(c);
            if (rendered < 8) Ui.reveal(c, rendered * 50L);
            rendered++;
            if (mode.equals("exported") && rendered >= 60) break;
        }
    }

    Button weightBtn(Button b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        b.setLayoutParams(lp);
        return b;
    }

    Button openBtn(final String pkg) {
        Button b = Ui.button(this, "▶ OPEN", Ui.GREEN);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent it = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (it != null) startActivity(it);
                    else toast("No launch screen");
                } catch (Exception e) {
                    toast("Cannot open");
                }
            }
        });
        return b;
    }

    Button apkBtn(final String label, final PackageInfo p) {
        Button b = Ui.button(this, "📦 APK", Ui.GREEN);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                extractApk(label, p);
            }
        });
        return b;
    }

    void appSettings(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + pkg)));
        } catch (Exception e) {
            toast("Cannot open settings");
        }
    }

    void extractApk(final String label, final PackageInfo p) {
        if (p.applicationInfo == null || p.applicationInfo.sourceDir == null) {
            toast("APK path unavailable");
            return;
        }
        toast("Extracting…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String safe = label.replaceAll("[^A-Za-z0-9._-]", "_") + "_" + p.versionName + ".apk";
                    String where;
                    if (Build.VERSION.SDK_INT >= 29) {
                        ContentValues cv = new ContentValues();
                        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, safe);
                        cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive");
                        cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
                        Uri uri = getContentResolver().insert(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                        if (uri == null) throw new Exception("MediaStore rejected the file");
                        InputStream in = new FileInputStream(p.applicationInfo.sourceDir);
                        OutputStream os = getContentResolver().openOutputStream(uri);
                        copyStream(in, os);
                        ContentValues done = new ContentValues();
                        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                        getContentResolver().update(uri, done, null, null);
                        where = "Downloads/" + safe;
                    } else {
                        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        if (!dir.exists()) dir.mkdirs();
                        File dst = new File(dir, safe);
                        copyStream(new FileInputStream(p.applicationInfo.sourceDir), new FileOutputStream(dst));
                        where = dst.getAbsolutePath();
                    }
                    toast("✓ Saved: " + where);
                } catch (Exception e) {
                    try {
                        // fallback: app-private Downloads dir (always writable)
                        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                        if (dir == null) dir = getFilesDir();
                        File dst = new File(dir, label.replaceAll("[^A-Za-z0-9._-]", "_") + ".apk");
                        copyStream(new FileInputStream(p.applicationInfo.sourceDir), new FileOutputStream(dst));
                        toast("✓ Saved (app folder): " + dst.getAbsolutePath());
                    } catch (Exception e2) {
                        toast("Extraction failed: " + e2.getMessage());
                    }
                }
            }
        }).start();
    }

    void copyStream(InputStream in, OutputStream os) throws Exception {
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
            try {
                os.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ---------------------------------------------------------------- task manager / RAM

    void taskManager() {
        final LinearLayout stats = Ui.card(this);
        renderMem(stats);
        out.addView(stats);
        Ui.reveal(stats);
        final Button ref = Ui.button(this, "⟳ REFRESH PROCESSES");
        ref.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                renderProcs();
                renderMem(stats);
            }
        });
        out.addView(ref);
        renderProcs();
    }

    void renderMem(LinearLayout stats) {
        stats.removeAllViews();
        stats.addView(Ui.text(this, "MEMORY", 11, Ui.GREEN, true));
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            am.getMemoryInfo(mi);
            double used = mi.totalMem - mi.availMem;
            stats.addView(Ui.text(this, "TOTAL:  " + Compute.fmtBytes(mi.totalMem)
                    + "\nUSED:   " + Compute.fmtBytes(used) + " (" + Ui.fmt(used * 100.0 / mi.totalMem, 1) + "%)"
                    + "\nFREE:   " + Compute.fmtBytes(mi.availMem)
                    + "\nLOW:    " + (mi.lowMemory ? "YES" : "NO"), 12, Ui.TEXT, false));
        } catch (Exception e) {
            stats.addView(Ui.text(this, "Memory info unavailable", 12, Ui.DIM, false));
        }
    }

    void renderProcs() {
        LinearLayout old = out.findViewWithTag("procs");
        if (old != null) out.removeView(old);
        final LinearLayout box = new LinearLayout(this);
        box.setTag("procs");
        box.setOrientation(LinearLayout.VERTICAL);
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> ps = am.getRunningAppProcesses();
            LinearLayout head = card("RUNNING PROCESSES: " + (ps == null ? 0 : ps.size()), null, Ui.GREEN);
            box.addView(head);
            if (ps != null) {
                for (int i = 0; i < ps.size() && i < 40; i++) {
                    ActivityManager.RunningAppProcessInfo pi = ps.get(i);
                    String imp = pi.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ? "FOREGROUND"
                            : pi.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED ? "CACHED" : "SERVICE";
                    final String pkg = pi.processName;
                    LinearLayout c = card(pi.processName, "PID " + pi.pid + " · " + imp, Ui.TEXT);
                    Button kill = Ui.button(this, "✕ KILL", Ui.GOLD);
                    kill.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                ActivityManager am2 = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                                am2.killBackgroundProcesses(pkg);
                                toast("Kill request sent (Android may restart it)");
                            } catch (Exception e) {
                                toast("Cannot kill " + pkg);
                            }
                        }
                    });
                    c.addView(kill);
                    box.addView(c);
                }
                if (ps.size() > 40) {
                    box.addView(Ui.text(this, "… " + (ps.size() - 40) + " more cached processes", 10, Ui.DIM, false));
                }
            }
        } catch (Exception e) {
            box.addView(card("Process list unavailable", "Some ROMs hide background processes. " + e.getMessage(), Ui.DIM));
        }
        out.addView(box);
    }

    void ramBoost() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.gold(this, "⚡ RAM BOOST", 16));
        final TextView tv = Ui.text(this, "", 13, Ui.TEXT, false);
        c.addView(tv);
        out.addView(c);
        Ui.pop(c);
        renderBoost(tv);
        Button boost = Ui.button(this, "🚀 BOOST NOW", Ui.GREEN);
        boost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                    List<ActivityManager.RunningAppProcessInfo> ps = am.getRunningAppProcesses();
                    int n = 0;
                    if (ps != null) {
                        for (ActivityManager.RunningAppProcessInfo pi : ps) {
                            try {
                                if (pi.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
                                        || pi.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                                    am.killBackgroundProcesses(pi.processName);
                                    n++;
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    System.gc();
                    renderBoost(tv);
                    toast("Sent " + n + " free-up requests");
                } catch (Exception e) {
                    toast("Boost failed");
                }
            }
        });
        out.addView(boost);
        LinearLayout note = card("How it works",
                "Android manages memory itself — killBackgroundProcesses() asks the system to free cached apps. "
                        + "Android restarts cached apps on demand, so RAM is always used; an 'empty RAM' phone is slower, not faster. "
                        + "This tool frees cached apps the safe way (no root, no force-stop crashes).", Ui.TEXT);
        out.addView(note);
    }

    void renderBoost(TextView tv) {
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            am.getMemoryInfo(mi);
            double used = mi.totalMem - mi.availMem;
            tv.setText("FREE RAM NOW:  " + Compute.fmtBytes(mi.availMem)
                    + "\nUSED:          " + Compute.fmtBytes(used) + " (" + Ui.fmt(used * 100.0 / mi.totalMem, 1) + "%)"
                    + "\nTOTAL:         " + Compute.fmtBytes(mi.totalMem));
        } catch (Exception e) {
            tv.setText("Memory info unavailable");
        }
    }

    // ---------------------------------------------------------------- cleaners

    void cacheCleaner() {
        final LinearLayout c = Ui.card(this);
        c.addView(Ui.gold(this, "🧹 CACHE CLEANER", 16));
        final TextView tv = Ui.text(this, "", 12, Ui.TEXT, false);
        c.addView(tv);
        out.addView(c);
        renderCache(tv);
        Button clean = Ui.button(this, "🧹 CLEAN APP CACHES", Ui.GREEN);
        clean.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    long freed = deleteDir(getCacheDir());
                    File cc = getExternalFilesDir(null);
                    if (cc != null) freed += deleteDir(cc);
                    File cod = getCacheDir().getParentFile();
                    if (cod != null) {
                        File codeCache = new File(cod, "code_cache");
                        if (codeCache.exists()) freed += deleteDir(codeCache);
                    }
                    renderCache(tv);
                    toast("Freed " + Compute.fmtBytes(freed));
                } catch (Exception e) {
                    toast("Clean failed: " + e.getMessage());
                }
            }
        });
        out.addView(clean);
        out.addView(card("About Android caches",
                "Each app's cache belongs to its own sandbox — Android does not let one app wipe another app's cache without root. "
                        + "This cleaner clears GIToolbox's own cache + temp files safely, and shows your total storage so you can spot space hogs with App Sizes. "
                        + "For other apps: open Settings → Storage to clear them system-wide.", Ui.TEXT));
    }

    void renderCache(TextView tv) {
        try {
            long cache = dirSize(getCacheDir());
            File cod = getCacheDir().getParentFile();
            if (cod != null) {
                File codeCache = new File(cod, "code_cache");
                if (codeCache.exists()) cache += dirSize(codeCache);
            }
            File ext = getExternalFilesDir(null);
            long extSize = ext == null ? 0 : dirSize(ext);
            File dataDir = Environment.getDataDirectory();
            StatFs st = new StatFs(dataDir.getAbsolutePath());
            tv.setText("GITOOLBOX CACHE:  " + Compute.fmtBytes(cache)
                    + "\nAPP FILES:         " + Compute.fmtBytes(extSize)
                    + "\n\nINTERNAL FREE:     " + Compute.fmtBytes(st.getAvailableBytes())
                    + " of " + Compute.fmtBytes(st.getTotalBytes()));
        } catch (Exception e) {
            tv.setText("Cache info unavailable");
        }
    }

    long dirSize(File dir) {
        long n = 0;
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) n += dirSize(f);
            else n += f.length();
        }
        return n;
    }

    long deleteDir(File dir) {
        long freed = 0;
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) freed += deleteDir(f);
            try {
                long len = f.length();
                if (f.delete()) freed += len;
            } catch (Exception ignored) {
            }
        }
        return freed;
    }

    // ---------------------------------------------------------------- permission / startup

    void permissionManager() {
        out.addView(card("Which apps use which permissions",
                "Pick a sensitive permission to list every installed app that requests it. 100% offline.", Ui.TEXT));
        String[][] perms = {
                {"📷 CAMERA", "android.permission.CAMERA"},
                {"🎤 MICROPHONE", "android.permission.RECORD_AUDIO"},
                {"📍 LOCATION", "android.permission.ACCESS_FINE_LOCATION"},
                {"📖 CONTACTS", "android.permission.READ_CONTACTS"},
                {"💾 STORAGE", "android.permission.READ_EXTERNAL_STORAGE"},
                {"✉ SMS", "android.permission.READ_SMS"},
                {"📞 CALL LOG", "android.permission.READ_CALL_LOG"},
                {"☎ CALL PHONE", "android.permission.CALL_PHONE"},
                {"📅 CALENDAR", "android.permission.READ_CALENDAR"},
                {"📱 PHONE STATE", "android.permission.READ_PHONE_STATE"},
                {"❤ BODY SENSORS", "android.permission.BODY_SENSORS"},
        };
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < perms.length; i += 2) {
            LinearLayout r = Ui.row(this);
            r.addView(weightBtn(permBtn(perms[i][0], perms[i][1])));
            if (i + 1 < perms.length) {
                Button b2 = permBtn(perms[i + 1][0], perms[i + 1][1]);
                LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                lp2.leftMargin = Ui.dp(this, 6);
                b2.setLayoutParams(lp2);
                r.addView(b2);
            }
            r.setPadding(0, Ui.dp(this, 4), 0, 0);
            grid.addView(r);
        }
        out.addView(grid);
    }

    Button permBtn(final String label, final String perm) {
        Button b = Ui.button(this, label, Ui.GREEN);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAppsForPerm(label, perm);
            }
        });
        return b;
    }

    void showAppsForPerm(final String label, final String perm) {
        final LinearLayout old = out.findViewWithTag("permresult");
        if (old != null) out.removeView(old);
        final LinearLayout box = new LinearLayout(this);
        box.setTag("permresult");
        box.setOrientation(LinearLayout.VERTICAL);
        final TextView loading = Ui.text(this, "Scanning…", 12, Ui.GOLD, true);
        box.addView(loading);
        out.addView(box);
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<PackageInfo> pkgs = new ArrayList<>();
                try {
                    pkgs = getPackageManager().getInstalledPackages(PackageManager.GET_PERMISSIONS);
                } catch (Exception ignored) {
                }
                final List<PackageInfo> hits = new ArrayList<>();
                for (PackageInfo p : pkgs) {
                    try {
                        if (p.requestedPermissions == null) continue;
                        for (String q : p.requestedPermissions) {
                            if (perm.equals(q)) {
                                hits.add(p);
                                break;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        StringBuilder sb = new StringBuilder();
                        sb.append("APPS REQUESTING ").append(label).append(": ").append(hits.size());
                        sb.append("\n\n");
                        PackageManager pm = getPackageManager();
                        int shown = 0;
                        for (PackageInfo p : hits) {
                            sb.append("• ").append(label(p, pm)).append("\n  ").append(p.packageName).append("\n");
                            if (++shown >= 60) {
                                sb.append("… and ").append(hits.size() - 60).append(" more");
                                break;
                            }
                        }
                        loading.setTextColor(Ui.TEXT);
                        loading.setText(sb.toString());
                        Ui.reveal(box);
                    }
                });
            }
        }).start();
    }

    void startupManager() {
        final TextView loading = Ui.text(this, "Scanning boot receivers…", 12, Ui.GOLD, true);
        out.addView(loading);
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<ResolveInfo> rs = new ArrayList<>();
                try {
                    rs = getPackageManager().queryBroadcastReceivers(
                            new Intent(Intent.ACTION_BOOT_COMPLETED), 0);
                } catch (Exception ignored) {
                }
                final List<ResolveInfo> list = rs;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (list.isEmpty()) {
                            loading.setText("No BOOT_COMPLETED receivers visible (some ROMs hide them).");
                            loading.setTextColor(Ui.DIM);
                            return;
                        }
                        out.removeView(loading);
                        out.addView(card("AUTO-START ON BOOT: " + list.size(),
                                "These receivers run every time your phone restarts.", Ui.GREEN));
                        PackageManager pm = getPackageManager();
                        for (int i = 0; i < list.size(); i++) {
                            ResolveInfo ri = list.get(i);
                            String pkg = ri.activityInfo != null ? ri.activityInfo.packageName : "?";
                            String act = ri.activityInfo != null ? ri.activityInfo.name : "?";
                            boolean enabled = true;
                            try {
                                enabled = getPackageManager().getComponentEnabledSetting(
                                        new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
                                        != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                            } catch (Exception ignored) {
                            }
                            final String fpkg = pkg;
                            LinearLayout c = card(label(pkg, pm), pkg + "\n" + act + (enabled ? "" : "\n· DISABLED"), enabled ? Ui.TEXT : Ui.DIM);
                            c.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    appSettings(fpkg);
                                }
                            });
                            out.addView(c);
                        }
                    }
                });
            }
        }).start();
    }

    String label(String pkg, PackageManager pm) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence c = pm.getApplicationLabel(ai);
            if (c != null) return c.toString();
        } catch (Exception ignored) {
        }
        return pkg;
    }

    // ---------------------------------------------------------------- data usage / net monitor

    void dataUsage() {
        try {
            long rx = TrafficStats.getTotalRxBytes();
            long tx = TrafficStats.getTotalTxBytes();
            long mrx = TrafficStats.getMobileRxBytes();
            long mtx = TrafficStats.getMobileTxBytes();
            StringBuilder sb = new StringBuilder();
            sb.append("SINCE BOOT\n\n");
            sb.append("TOTAL ↓:  ").append(rx < 0 ? "?" : Compute.fmtBytes(rx)).append("\n");
            sb.append("TOTAL ↑:  ").append(tx < 0 ? "?" : Compute.fmtBytes(tx)).append("\n\n");
            sb.append("MOBILE ↓: ").append(mrx < 0 ? "?" : Compute.fmtBytes(mrx)).append("\n");
            sb.append("MOBILE ↑: ").append(mtx < 0 ? "?" : Compute.fmtBytes(mtx)).append("\n");
            out.addView(card("DEVICE DATA USAGE", sb.toString(), Ui.TEXT));
        } catch (Exception e) {
            out.addView(card("Data usage", "Unavailable: " + e.getMessage(), Ui.DIM));
        }
        final TextView loading = Ui.text(this, "Ranking apps by data usage…", 12, Ui.GOLD, true);
        out.addView(loading);
        new Thread(new Runnable() {
            @Override
            public void run() {
                class Row {
                    String name;
                    long bytes;
                }
                List<Row> rows = new ArrayList<>();
                try {
                    PackageManager pm = getPackageManager();
                    List<PackageInfo> pkgs = pm.getInstalledPackages(0);
                    for (PackageInfo p : pkgs) {
                        try {
                            int uid = p.applicationInfo.uid;
                            long r = TrafficStats.getUidRxBytes(uid);
                            long t = TrafficStats.getUidTxBytes(uid);
                            if (r > 0 || t > 0) {
                                Row row = new Row();
                                row.name = label(p, pm);
                                row.bytes = Math.max(0, r) + Math.max(0, t);
                                rows.add(row);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    Collections.sort(rows, new Comparator<Row>() {
                        @Override
                        public int compare(Row a, Row b) {
                            return Long.compare(b.bytes, a.bytes);
                        }
                    });
                } catch (Exception ignored) {
                }
                final List<Row> fr = rows;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        out.removeView(loading);
                        if (fr.isEmpty()) {
                            out.addView(card("Per-app usage", "No per-app counters available on this ROM (TrafficStats unsupported).", Ui.DIM));
                            return;
                        }
                        StringBuilder sb = new StringBuilder("TOP APPS BY DATA\n\n");
                        int n = Math.min(15, fr.size());
                        for (int i = 0; i < n; i++) {
                            sb.append(i + 1).append(". ").append(fr.get(i).name)
                                    .append(" — ").append(Compute.fmtBytes(fr.get(i).bytes)).append("\n");
                        }
                        out.addView(card("PER-APP (since boot)", sb.toString(), Ui.TEXT));
                    }
                });
            }
        }).start();
    }

    void networkMonitor() {
        final LinearLayout c = Ui.card(this);
        c.addView(Ui.gold(this, "📡 LIVE NETWORK MONITOR", 14));
        final TextView tv = Ui.text(this, "Measuring…", 16, Ui.GREEN, true);
        tv.setPadding(0, Ui.dp(this, 8), 0, 0);
        c.addView(tv);
        final TextView tot = Ui.text(this, "", 12, Ui.TEXT, false);
        c.addView(tot);
        out.addView(c);
        Ui.pop(c);
        final long[] last = {TrafficStats.getTotalRxBytes(), TrafficStats.getTotalTxBytes()};
        final long[] lastT = {SystemClock.elapsedRealtime()};
        final Runnable[] tick = new Runnable[1];
        tick[0] = new Runnable() {
            @Override
            public void run() {
                try {
                    long rx = TrafficStats.getTotalRxBytes();
                    long tx = TrafficStats.getTotalTxBytes();
                    long now = SystemClock.elapsedRealtime();
                    double secs = Math.max(0.25, (now - lastT[0]) / 1000.0);
                    double dR = (rx - last[0]) / secs;
                    double dT = (tx - last[1]) / secs;
                    last[0] = rx;
                    last[1] = tx;
                    lastT[0] = now;
                    tv.setText("↓ " + rate(dR) + "    ↑ " + rate(dT));
                    tot.setText("TOTAL ↓ " + (rx < 0 ? "?" : Compute.fmtBytes(rx))
                            + "   ↑ " + (tx < 0 ? "?" : Compute.fmtBytes(tx))
                            + "\nINTERFACE: " + (isOnline() ? "CONNECTED" : "OFFLINE"));
                } catch (Exception ignored) {
                }
                h.postDelayed(tick[0], 1000);
            }
        };
        h.postDelayed(tick[0], 600);
        out.addView(card("Live RX/TX", "Updates every second using the kernel's network counters. Leave this screen open to watch streaming or download rates.", Ui.TEXT));
    }

    String rate(double bps) {
        if (bps < 0) return "?";
        if (bps < 1024) return Ui.fmt(bps, 0) + " B/s";
        if (bps < 1024 * 1024) return Ui.fmt(bps / 1024.0, 1) + " KB/s";
        return Ui.fmt(bps / 1048576.0, 2) + " MB/s";
    }

    boolean isOnline() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getSystemService(CONNECTIVITY_SERVICE);
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- clipboard

    void clipboardManager() {
        final android.content.SharedPreferences prefs = getSharedPreferences("gitoolbox_sys", MODE_PRIVATE);
        LinearLayout top = Ui.card(this);
        final TextView cur = Ui.text(this, "", 12, Ui.TEXT, false);
        top.addView(Ui.gold(this, "📋 CURRENT CLIPBOARD", 12));
        top.addView(cur);
        out.addView(top);
        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                cur.setText(clipText());
            }
        };
        refresh.run();

        LinearLayout row = Ui.row(this);
        Button save = Ui.button(this, "＋ SAVE CLIP", Ui.GREEN);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = clipText();
                if (t.length() == 0) {
                    toast("Clipboard is empty");
                    return;
                }
                List<String> items = clips(prefs);
                items.add(0, t);
                saveClips(prefs, items);
                refresh.run();
                renderClips(prefs);
                toast("Saved to history");
            }
        });
        LinearLayout.LayoutParams s1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        save.setLayoutParams(s1);
        Button clr = Ui.button(this, "🗑 CLEAR ALL", Ui.GOLD);
        LinearLayout.LayoutParams s2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        s2.leftMargin = Ui.dp(this, 6);
        clr.setLayoutParams(s2);
        clr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("", ""));
                } catch (Exception ignored) {
                }
                refresh.run();
                toast("Clipboard cleared");
            }
        });
        row.addView(save);
        row.addView(clr);
        out.addView(row);

        final LinearLayout box = new LinearLayout(this);
        box.setTag("clips");
        box.setOrientation(LinearLayout.VERTICAL);
        out.addView(box);
        renderClips(prefs);
    }

    String clipText() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (!cm.hasPrimaryClip() || cm.getPrimaryClip() == null) return "";
            ClipData.Item it = cm.getPrimaryClip().getItemAt(0);
            CharSequence t = it.coerceToText(SysActivity.this);
            return t == null ? "" : t.toString();
        } catch (Exception e) {
            return "";
        }
    }

    List<String> clips(android.content.SharedPreferences prefs) {
        List<String> items = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString("clips", "[]"));
            for (int i = 0; i < a.length(); i++) items.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return items;
    }

    void saveClips(android.content.SharedPreferences prefs, List<String> items) {
        JSONArray a = new JSONArray();
        for (String s : items) a.put(s);
        prefs.edit().putString("clips", a.toString()).apply();
    }

    void renderClips(final android.content.SharedPreferences prefs) {
        LinearLayout box = out.findViewWithTag("clips");
        if (box == null) return;
        box.removeAllViews();
        box.addView(Ui.text(this, "HISTORY", 11, Ui.GREEN, true));
        List<String> items = clips(prefs);
        if (items.isEmpty()) {
            box.addView(Ui.text(this, "No saved clips yet.\nCopy something, then tap SAVE CLIP ☝", 12, Ui.DIM, false));
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            final String text = items.get(i);
            LinearLayout c = Ui.card(this);
            String shown = text.length() > 160 ? text.substring(0, 160) + "…" : text;
            c.addView(Ui.text(this, shown, 12, Ui.TEXT, false));
            Button cp = Ui.button(this, "📋 COPY", Ui.GREEN);
            cp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("GIToolbox", text));
                        toast("Copied");
                    } catch (Exception ignored) {
                    }
                }
            });
            LinearLayout r = Ui.row(this);
            r.addView(weightBtn(cp));
            Button del = Ui.button(this, "✕", Ui.GOLD);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<String> items2 = clips(prefs);
                    items2.remove(idx);
                    saveClips(prefs, items2);
                    renderClips(prefs);
                }
            });
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            dlp.leftMargin = Ui.dp(this, 6);
            del.setLayoutParams(dlp);
            r.addView(del);
            c.addView(r);
            box.addView(c);
        }
    }

    // ---------------------------------------------------------------- quick toggles

    void keepScreenOn() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.gold(this, "💡 KEEP SCREEN ON", 16));
        final TextView tv = Ui.text(this, "", 12, Ui.TEXT, false);
        c.addView(tv);
        out.addView(c);
        Ui.pop(c);
        final Runnable show = new Runnable() {
            @Override
            public void run() {
                tv.setText(keepOn ? "STATUS: ON — this screen will not sleep while you are here."
                        : "STATUS: OFF — screen sleeps on its normal timeout.\n\nTap the button to hold the screen awake (great for reading, GPS navigation, recipes, debugging).");
            }
        };
        show.run();
        Button t = Ui.button(this, "⏻ TOGGLE", Ui.GREEN);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                keepOn = !keepOn;
                if (keepOn) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                show.run();
                toast(keepOn ? "Screen will stay on" : "Normal timeout restored");
            }
        });
        out.addView(t);
    }

    void volumeManager() {
        out.addView(card("SYSTEM STREAMS", "Drag a slider to set each stream's volume instantly.", Ui.TEXT));
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            Object[][] streams = {
                    {AudioManager.STREAM_MUSIC, "🎵 MEDIA"},
                    {AudioManager.STREAM_RING, "📳 RING"},
                    {AudioManager.STREAM_ALARM, "⏰ ALARM"},
                    {AudioManager.STREAM_NOTIFICATION, "🔔 NOTIFICATIONS"},
                    {AudioManager.STREAM_SYSTEM, "🖥 SYSTEM"},
            };
            for (Object[] s : streams) {
                final int stream = (Integer) s[0];
                int max = 0;
                try {
                    max = am.getStreamMaxVolume(stream);
                } catch (Exception ignored) {
                }
                if (max <= 0) continue;
                int cur = 0;
                try {
                    cur = am.getStreamVolume(stream);
                } catch (Exception ignored) {
                }
                LinearLayout c = Ui.card(this);
                c.addView(Ui.text(this, s[1] + "  ·  " + cur + " / " + max, 12, Ui.TEXT, true));
                SeekBar sb = new SeekBar(this);
                sb.setMax(max);
                sb.setProgress(cur);
                sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (!fromUser) return;
                        try {
                            AudioManager am2 = (AudioManager) getSystemService(AUDIO_SERVICE);
                            am2.setStreamVolume(stream, progress, 0);
                        } catch (Exception e) {
                            toast("Blocked (Do-Not-Disturb or policy)");
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                c.addView(sb);
                out.addView(c);
                Ui.reveal(c);
            }
        } catch (Exception e) {
            out.addView(card("Volume Manager", "Unavailable: " + e.getMessage(), Ui.DIM));
        }
    }

    void soundProfile() {
        try {
            final AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            final LinearLayout c = Ui.card(this);
            c.addView(Ui.gold(this, "PROFILE", 14));
            final TextView tv = Ui.text(this, "", 13, Ui.TEXT, true);
            c.addView(tv);
            out.addView(c);
            Runnable show = new Runnable() {
                @Override
                public void run() {
                    int m = am.getRingerMode();
                    tv.setText("CURRENT: " + (m == AudioManager.RINGER_MODE_NORMAL ? "🔊 NORMAL"
                            : m == AudioManager.RINGER_MODE_VIBRATE ? "📳 VIBRATE" : "🔇 SILENT"));
                }
            };
            show.run();
            Button normal = Ui.button(this, "🔊 NORMAL", Ui.GREEN);
            normal.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        toast("Normal");
                    } catch (Exception e) {
                        toast("Blocked by device policy");
                    }
                    recreate();
                }
            });
            Button vib = Ui.button(this, "📳 VIBRATE", Ui.GREEN);
            vib.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                        toast("Vibrate");
                    } catch (Exception e) {
                        toast("Blocked by device policy");
                    }
                    recreate();
                }
            });
            Button silent = Ui.button(this, "🔇 SILENT", Ui.GOLD);
            silent.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                        toast("Silent");
                    } catch (Exception e) {
                        toast("Blocked by device policy");
                    }
                    recreate();
                }
            });
            LinearLayout r = Ui.row(this);
            r.addView(weightBtn(normal));
            LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            p2.leftMargin = Ui.dp(this, 6);
            vib.setLayoutParams(p2);
            r.addView(vib);
            LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            p3.leftMargin = Ui.dp(this, 6);
            silent.setLayoutParams(p3);
            r.addView(silent);
            out.addView(r);
        } catch (Exception e) {
            out.addView(card("Sound Profile", "Unavailable: " + e.getMessage(), Ui.DIM));
        }
    }

    void ringtonePicker() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.gold(this, "🎵 RINGTONE PICKER", 16));
        c.addView(Ui.text(this, "Opens the system ringtone chooser (ringtone, notification or alarm). Pick any sound on your phone.", 12, Ui.TEXT, false));
        final TextView picked = Ui.text(this, "", 12, Ui.GREEN, true);
        picked.setTag("picked");
        c.addView(picked);
        out.addView(c);
        Button pick = Ui.button(this, "🎵 CHOOSE SOUND", Ui.GREEN);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent it = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                    it.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL);
                    it.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                    startActivityForResult(it, 901);
                } catch (Exception e) {
                    toast("No ringtone picker on this device");
                }
            }
        });
        out.addView(pick);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 901 || resultCode != RESULT_OK || data == null) return;
        try {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri == null) return;
            Ringtone r = RingtoneManager.getRingtone(this, uri);
            if (r != null) {
                String title = r.getTitle(this);
                toast("Picked: " + title);
                TextView tv = out.findViewWithTag("picked");
                if (tv != null) tv.setText("PICKED: " + title);
            }
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- inspectors

    void refreshRate() {
        try {
            float hz = getWindowManager().getDefaultDisplay().getRefreshRate();
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = getResources().getDisplayMetrics().heightPixels;
            float d = getResources().getDisplayMetrics().density;
            out.addView(card("DISPLAY", "REFRESH RATE: " + Ui.fmt(hz, 1) + " Hz"
                    + "\nRESOLUTION: " + w + " x " + h + " px"
                    + "\nLOGICAL SIZE: " + Ui.fmt(w / d, 1) + " x " + Ui.fmt(h / d, 1) + " dp"
                    + "\nDENSITY: " + getResources().getDisplayMetrics().densityDpi + " dpi"
                    + "\n\nNote: 60 Hz is standard; 90/120 Hz screens report their active rate here.", Ui.TEXT));
        } catch (Exception e) {
            out.addView(card("Display", "Unavailable: " + e.getMessage(), Ui.DIM));
        }
    }

    void webViewVersion() {
        String s;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                android.content.pm.PackageInfo p = android.webkit.WebView.getCurrentWebViewPackage();
                if (p != null) {
                    s = "WEBVIEW: " + (p.versionName == null ? "?" : p.versionName)
                            + "\nPACKAGE: " + p.packageName
                            + "\nVERSION CODE: " + p.versionCode
                            + "\n\nThis is the engine behind every in-app browser on your phone (Chrome-based system WebView). Keeping it updated closes security holes.";
                } else {
                    s = "No WebView provider installed.";
                }
            } else {
                s = "WebView package API needs Android 8.0+.";
            }
        } catch (Exception e) {
            s = "Unavailable: " + e.getMessage();
        }
        out.addView(card("WEBVIEW ENGINE", s, Ui.TEXT));
    }

    void keyboardList() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            List<InputMethodInfo> ims = imm.getEnabledInputMethodList();
            StringBuilder sb = new StringBuilder("ENABLED KEYBOARDS: " + ims.size() + "\n\n");
            for (InputMethodInfo m : ims) {
                sb.append("• ").append(m.loadLabel(getPackageManager()))
                        .append("\n  ").append(m.getPackageName()).append("\n");
            }
            sb.append("\nInstalled-but-disabled keyboards are hidden by the system.");
            out.addView(card("INPUT METHODS", sb.toString(), Ui.TEXT));
        } catch (Exception e) {
            out.addView(card("Input methods", "Unavailable: " + e.getMessage(), Ui.DIM));
        }
    }

    void ttsEngines() {
        try {
            List<ResolveInfo> rs = getPackageManager().queryIntentServices(
                    new Intent("android.intent.action.TTS_SERVICE"), 0);
            StringBuilder sb = new StringBuilder("TTS ENGINES: " + rs.size() + "\n\n");
            for (ResolveInfo ri : rs) {
                sb.append("• ").append(ri.loadLabel(getPackageManager()))
                        .append("\n  ").append(ri.serviceInfo.packageName).append("\n");
            }
            out.addView(card("TEXT-TO-SPEECH", sb.toString(), Ui.TEXT));
        } catch (Exception e) {
            out.addView(card("TTS", "Unavailable: " + e.getMessage(), Ui.DIM));
        }
    }

    void accessibilityChecker() {
        try {
            String acc = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String nl = Settings.Secure.getString(getContentResolver(),
                    "enabled_notification_listeners");
            StringBuilder sb = new StringBuilder("ACCESSIBILITY SERVICES\n");
            if (acc == null || acc.length() == 0) sb.append("· none enabled ✓\n");
            else {
                sb.append("\n");
                for (String s : acc.split(":")) sb.append("• ").append(s).append("\n");
            }
            sb.append("\nNOTIFICATION LISTENERS\n");
            if (nl == null || nl.length() == 0) sb.append("· none enabled ✓\n");
            else {
                sb.append("\n");
                for (String s : nl.split(":")) sb.append("• ").append(s).append("\n");
            }
            sb.append("\nEvery enabled accessibility service can read screen content. "
                    + "Only keep services you trust — spyware apps often hide here.");
            out.addView(card("PRIVACY AUDIT", sb.toString(), Ui.TEXT));
        } catch (Exception e) {
            out.addView(card("Privacy audit", "Unavailable: " + e.getMessage(), Ui.DIM));
        }
    }

    void uptime() {
        try {
            long up = SystemClock.elapsedRealtime();
            long days = up / 86400000L;
            long hours = (up % 86400000L) / 3600000L;
            long mins = (up % 3600000L) / 60000L;
            long awake = SystemClock.uptimeMillis();
            StringBuilder sb = new StringBuilder();
            sb.append("TIME SINCE BOOT: ").append(days).append("d ").append(hours).append("h ").append(mins).append("m\n");
            sb.append("ELAPSED REALTIME: ").append(up / 1000L).append(" s\n");
            sb.append("AWAKE TIME (no deep sleep): ").append(awake / 1000L).append(" s\n");
            sb.append("DEEP SLEEP: ").append(Math.max(0, (up - awake) / 1000L)).append(" s\n\n");
            sb.append("Deep sleep = CPU resting in low-power mode. A healthy phone sleeps deeply when idle; poor deep sleep drains battery.");
            out.addView(card("UPTIME", sb.toString(), Ui.TEXT));
        } catch (Exception e) {
            out.addView(card("Uptime", "Unavailable: " + e.getMessage(), Ui.DIM));
        }
    }

    void kernelInfo() {
        String ver = readOne("/proc/version");
        StringBuilder sb = new StringBuilder();
        sb.append("KERNEL:\n").append(ver == null ? "unknown" : ver).append("\n\n");
        sb.append("ARCH: ").append(System.getProperty("os.arch")).append("\n");
        sb.append("JAVA VM: ").append(System.getProperty("java.vm.name"))
                .append(" ").append(System.getProperty("java.vm.version")).append("\n");
        sb.append("BOOTLOADER: ").append(Build.BOOTLOADER).append("\n");
        sb.append("FINGERPRINT: ").append(Build.FINGERPRINT).append("\n");
        try {
            sb.append("RADIO: ").append(Build.getRadioVersion()).append("\n");
        } catch (Exception ignored) {
        }
        sb.append("\nANDROID ID: ").append(Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
        out.addView(card("KERNEL & BUILD", sb.toString(), Ui.TEXT));
    }

    String readOne(String path) {
        try {
            FileInputStream in = new FileInputStream(path);
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[4096];
            int n = in.read(buf);
            in.close();
            for (int i = 0; i < n && i < 1000; i++) sb.append((char) buf[i]);
            return sb.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }

    void storageVolumes() {
        StringBuilder sb = new StringBuilder();
        try {
            StorageManager sm = (StorageManager) getSystemService(STORAGE_SERVICE);
            List<StorageVolume> vols = sm.getStorageVolumes();
            sb.append("STORAGE VOLUMES: ").append(vols.size()).append("\n\n");
            for (StorageVolume v : vols) {
                try {
                    sb.append("• ").append(v.getState())
                            .append(v.isRemovable() ? " · REMOVABLE" : " · BUILT-IN")
                            .append(v.isEmulated() ? " · EMULATED" : "")
                            .append("\n");
                    if (Build.VERSION.SDK_INT >= 28) {
                        sb.append("  ").append(v.getDescription(SysActivity.this)).append("\n");
                    }
                    if (Build.VERSION.SDK_INT >= 30) {
                        File dir = v.getDirectory();
                        if (dir != null) {
                            StatFs st = new StatFs(dir.getAbsolutePath());
                            sb.append("  total ").append(Compute.fmtBytes(st.getTotalBytes()))
                                    .append(" · free ").append(Compute.fmtBytes(st.getAvailableBytes())).append("\n");
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            sb.append("Volume list unavailable: ").append(e.getMessage());
        }
        sb.append("\nINTERNAL DATA\n");
        appendStat(sb, Environment.getDataDirectory());
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            sb.append("\nSHARED STORAGE\n");
            appendStat(sb, Environment.getExternalStorageDirectory());
        }
        sb.append("\nTHIS APP (files dir)\n");
        appendStat(sb, getFilesDir());
        out.addView(card("STORAGE MAP", sb.toString(), Ui.TEXT));
    }

    void appendStat(StringBuilder sb, File dir) {
        try {
            StatFs st = new StatFs(dir.getAbsolutePath());
            sb.append("Path: ").append(dir.getAbsolutePath())
                    .append("\nTotal:  ").append(Compute.fmtBytes(st.getTotalBytes()))
                    .append("\nFree:   ").append(Compute.fmtBytes(st.getAvailableBytes()))
                    .append("\nUsed:   ").append(Compute.fmtBytes(st.getTotalBytes() - st.getAvailableBytes()))
                    .append("\n");
        } catch (Exception e) {
            sb.append("Path: ").append(dir.getAbsolutePath()).append("\nUnavailable\n");
        }
    }

    void infoCard(String title, String body) {
        LinearLayout c = card(title, body, Ui.TEXT);
        out.addView(c);
        Ui.reveal(c);
    }
}
