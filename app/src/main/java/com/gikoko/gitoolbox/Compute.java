package com.gikoko.gitoolbox;

import java.util.HashMap;
import java.util.Map;

/** Core of the compute-tool registry. Feature packs register themselves here. */
public final class Compute {

    public interface Fn {
        String run(Map<String, String> in) throws Exception;
    }

    public static class Spec {
        public final String[][] fields;
        public final String button;
        public final Fn fn;

        Spec(String[][] fields, String button, Fn fn) {
            this.fields = fields;
            this.button = button;
            this.fn = fn;
        }
    }

    static final HashMap<String, Spec> MAP = new HashMap<>();

    static {
        ComputeText.register();
        ComputeConvert.register();
        ComputeCalc.register();
        ComputeSecurity.register();
        ComputeAi.register();
        ComputeUtil.register();
    }

    Compute() {
    }

    public static void reg(String name, String[][] fields, String button, Fn fn) {
        MAP.put(name, new Spec(fields, button, fn));
    }

    public static boolean has(String tool) {
        return MAP.containsKey(tool);
    }

    public static Spec get(String tool) {
        return MAP.get(tool);
    }

    // ---- shared helpers ----

    public static String s(Map<String, String> in, String k) {
        String v = in.get(k);
        return v == null ? "" : v.trim();
    }

    public static double d(Map<String, String> in, String k) throws Exception {
        String v = s(in, k);
        if (v.length() == 0) throw new Exception("Enter a number");
        try {
            return Double.parseDouble(v.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new Exception("Not a valid number: " + v);
        }
    }

    public static long L(Map<String, String> in, String k) throws Exception {
        return Math.round(d(in, k));
    }

    public static int i(Map<String, String> in, String k) throws Exception {
        return (int) L(in, k);
    }

    public static String lines(Map<String, String> in, String k) {
        String v = in.get(k);
        return v == null ? "" : v;
    }

    public static String join(String[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    public static String join(double[] arr, int places) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(Ui.fmt(arr[i], places));
        }
        return sb.toString();
    }

    public static String fmtBytes(double b) {
        String[] u = {"B", "KB", "MB", "GB", "TB"};
        int i = 0;
        while (b >= 1024 && i < u.length - 1) {
            b /= 1024;
            i++;
        }
        return Ui.fmt(b, b < 10 && i > 0 ? 2 : 0) + " " + u[i];
    }
}
