package com.gikoko.gitoolbox;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Converter tools (24; Currency Rates lives in NetActivity). */
final class ComputeConvert {

    private ComputeConvert() {
    }

    // factor tables: unit -> factor in SI base
    static final HashMap<String, Double> LEN = new HashMap<>();
    static final HashMap<String, Double> WT = new HashMap<>();
    static final HashMap<String, Double> SPD = new HashMap<>();
    static final HashMap<String, Double> AREA = new HashMap<>();
    static final HashMap<String, Double> VOL = new HashMap<>();
    static final HashMap<String, Double> DATA = new HashMap<>();
    static final HashMap<String, Double> TIME = new HashMap<>();
    static final HashMap<String, Double> ANG = new HashMap<>();
    static final HashMap<String, Double> PRES = new HashMap<>();
    static final HashMap<String, Double> EN = new HashMap<>();
    static final HashMap<String, Double> PWR = new HashMap<>();

    static {
        put(LEN, "mm", 0.001, "cm", 0.01, "m", 1.0, "km", 1000.0,
                "in", 0.0254, "ft", 0.3048, "yd", 0.9144, "mi", 1609.344,
                "nmi", 1852.0, "um", 1e-6, "nm", 1e-9);
        put(WT, "mg", 1e-6, "g", 0.001, "kg", 1.0, "t", 1000.0,
                "oz", 0.028349523125, "lb", 0.45359237, "st", 6.35029318, "ton", 907.18474);
        put(SPD, "m/s", 1.0, "km/h", 0.2777777778, "mph", 0.44704, "kn", 0.5144444444,
                "ft/s", 0.3048, "mach", 340.29, "c", 299792458.0);
        put(AREA, "mm2", 1e-6, "cm2", 1e-4, "m2", 1.0, "km2", 1e6, "ha", 1e4,
                "in2", 6.4516e-4, "ft2", 0.09290304, "yd2", 0.83612736, "acre", 4046.8564224, "mi2", 2589988.110336);
        put(VOL, "ml", 0.001, "l", 1.0, "m3", 1000.0, "cm3", 0.001, "gal", 3.785411784,
                "qt", 0.946352946, "pt", 0.473176473, "cup", 0.2365882365, "floz", 0.0295735295625, "tbsp", 0.0147867678125, "tsp", 0.00492892159);
        put(DATA, "bit", 1.0, "byte", 8.0, "kb", 8e3, "mb", 8e6, "gb", 8e9, "tb", 8e12,
                "kib", 8192.0, "mib", 8388608.0, "gib", 8589934592.0);
        put(TIME, "ms", 0.001, "s", 1.0, "min", 60.0, "h", 3600.0, "day", 86400.0, "week", 604800.0, "year", 31557600.0);
        put(ANG, "deg", 1.0, "rad", 57.29577951308232, "grad", 0.9, "turn", 360.0, "arcmin", 1.0 / 60.0, "arcsec", 1.0 / 3600.0);
        put(PRES, "pa", 1.0, "kpa", 1000.0, "mpa", 1e6, "bar", 1e5, "mbar", 100.0, "atm", 101325.0,
                "psi", 6894.757293168, "torr", 133.322368421, "mmhg", 133.322387415, "inhg", 3386.388);
        put(EN, "j", 1.0, "kj", 1000.0, "cal", 4.184, "kcal", 4184.0, "wh", 3600.0, "kwh", 3.6e6,
                "btu", 1055.05585262, "ev", 1.602176634e-19, "ftlb", 1.3558179483314);
        put(PWR, "w", 1.0, "kw", 1000.0, "mw", 1e6, "hp", 745.6998715823, "ps", 735.49875,
                "btu/h", 0.2930710702, "ftlb/s", 1.3558179483314);
    }

    static void put(HashMap<String, Double> m, Object... kv) {
        for (int i = 0; i < kv.length; i += 2) m.put(((String) kv[i]).toLowerCase(), (Double) kv[i + 1]);
    }

    static String conv(Map<String, String> in, HashMap<String, Double> tab, String title) throws Exception {
        double v = Compute.d(in, "f0");
        String f = Compute.s(in, "f1").toLowerCase();
        String t = Compute.s(in, "f2").toLowerCase();
        Double a = tab.get(f), b = tab.get(t);
        if (a == null || b == null)
            throw new Exception("Unknown unit. Use: " + keys(tab));
        return title + " result:\n" + Ui.fmt(v) + " " + f + "  =  " + Ui.fmt(v * a / b) + " " + t
                + "\n\nUnits: " + keys(tab);
    }

    static String keys(HashMap<String, Double> m) {
        StringBuilder sb = new StringBuilder();
        for (String k : m.keySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(k);
        }
        return sb.toString();
    }

    static void regFactor(String name, final HashMap<String, Double> tab, String hintUnits) {
        Compute.reg(name, new String[][]{
                ComputeText.fl("VALUE", "Enter value"),
                ComputeText.fl("FROM", hintUnits),
                ComputeText.fl("TO", hintUnits)
        }, "CONVERT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                return conv(in, tab, name.replace(" Converter", "").replace("Data Size", "Data size").trim());
            }
        });
    }

    static void register() {
        // master Unit Converter
        Compute.reg("Unit Converter", new String[][]{
                ComputeText.fl("GROUP", "length / weight / speed / area / volume / data / time / angle / pressure / energy / power"),
                ComputeText.fl("VALUE", "Enter value"),
                ComputeText.fl("FROM", "from unit, e.g. m"),
                ComputeText.fl("TO", "to unit, e.g. ft")
        }, "CONVERT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String g = Compute.s(in, "f0").toLowerCase();
                HashMap<String, Double> tab;
                if (g.startsWith("len")) tab = LEN;
                else if (g.startsWith("wei")) tab = WT;
                else if (g.startsWith("spd") || g.startsWith("speed")) tab = SPD;
                else if (g.startsWith("are")) tab = AREA;
                else if (g.startsWith("vol")) tab = VOL;
                else if (g.startsWith("dat")) tab = DATA;
                else if (g.startsWith("tim")) tab = TIME;
                else if (g.startsWith("ang")) tab = ANG;
                else if (g.startsWith("pre")) tab = PRES;
                else if (g.startsWith("ene")) tab = EN;
                else if (g.startsWith("pow")) tab = PWR;
                else throw new Exception("Unknown group: " + g);
                Map<String, String> sub = new HashMap<>(in);
                sub.put("f0", in.get("f1"));
                sub.put("f1", in.get("f2"));
                sub.put("f2", in.get("f3"));
                return conv(sub, tab, g);
            }
        });

        regFactor("Length Converter", LEN, "mm cm m km in ft yd mi nmi um nm");
        regFactor("Weight Converter", WT, "mg g kg t oz lb st ton");
        regFactor("Speed Converter", SPD, "m/s km/h mph kn ft/s mach c");
        regFactor("Area Converter", AREA, "mm2 cm2 m2 km2 ha in2 ft2 yd2 acre mi2");
        regFactor("Volume Converter", VOL, "ml l m3 cm3 gal qt pt cup floz tbsp tsp");
        regFactor("Data Size", DATA, "bit byte kb mb gb tb kib mib gib");
        regFactor("Time Converter", TIME, "ms s min h day week year");
        regFactor("Angle Converter", ANG, "deg rad grad turn arcmin arcsec");
        regFactor("Pressure Converter", PRES, "pa kpa mpa bar mbar atm psi torr mmhg inhg");
        regFactor("Energy Converter", EN, "j kj cal kcal wh kwh btu ev ftlb");
        regFactor("Power Converter", PWR, "w kw mw hp ps btu/h ftlb/s");

        // Temperature (formulas)
        Compute.reg("Temperature", new String[][]{
                ComputeText.fl("VALUE", "Enter value"),
                ComputeText.fl("FROM", "c / f / k"),
                ComputeText.fl("TO", "c / f / k")
        }, "CONVERT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double v = Compute.d(in, "f0");
                String f = Compute.s(in, "f1").toLowerCase();
                String t = Compute.s(in, "f2").toLowerCase();
                double c;
                if (f.equals("c")) c = v;
                else if (f.equals("f")) c = (v - 32) * 5 / 9.0;
                else if (f.equals("k")) c = v - 273.15;
                else throw new Exception("FROM must be c, f or k");
                double r;
                if (t.equals("c")) r = c;
                else if (t.equals("f")) r = c * 9 / 5.0 + 32;
                else if (t.equals("k")) r = c + 273.15;
                else throw new Exception("TO must be c, f or k");
                return Ui.fmt(v) + " °" + f.toUpperCase() + "  =  " + Ui.fmt(r) + " °" + t.toUpperCase();
            }
        });

        // Fuel Economy
        Compute.reg("Fuel Economy", new String[][]{
                ComputeText.fl("VALUE", "Enter value"),
                ComputeText.fl("FROM", "mpg / l/100km / km/l"),
                ComputeText.fl("TO", "mpg / l/100km / km/l")
        }, "CONVERT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double v = Compute.d(in, "f0");
                String f = Compute.s(in, "f1").toLowerCase().replace(" ", "");
                String t = Compute.s(in, "f2").toLowerCase().replace(" ", "");
                double kml;
                if (f.equals("mpg")) kml = v * 0.425143707;
                else if (f.equals("l/100km") || f.equals("l100km")) kml = v <= 0 ? 0 : 100.0 / v;
                else if (f.equals("km/l") || f.equals("kml")) kml = v;
                else throw new Exception("FROM must be mpg, l/100km or km/l");
                double r;
                if (t.equals("mpg")) r = kml / 0.425143707;
                else if (t.equals("l/100km") || t.equals("l100km")) r = kml <= 0 ? 0 : 100.0 / kml;
                else if (t.equals("km/l") || t.equals("kml")) r = kml;
                else throw new Exception("TO must be mpg, l/100km or km/l");
                return Ui.fmt(v) + " " + f + "  =  " + Ui.fmt(r) + " " + t;
            }
        });

        // Shoe Size
        Compute.reg("Shoe Size", new String[][]{
                ComputeText.fl("VALUE", "Enter size"),
                ComputeText.fl("FROM", "us / uk / eu / cm")
        }, "CONVERT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double v = Compute.d(in, "f0");
                String f = Compute.s(in, "f1").toLowerCase();
                double cm;
                if (f.equals("us")) cm = v * 0.8382 + 21.6;      // US men
                else if (f.equals("uk")) cm = v * 0.8467 + 22.1;
                else if (f.equals("eu")) cm = (v + 1.5) * 2 / 3.0 * 0.667 + 15.5;
                else if (f.equals("cm")) cm = v;
                else throw new Exception("FROM must be us, uk, eu or cm");
                double us = (cm - 21.6) / 0.8382;
                double uk = (cm - 22.1) / 0.8467;
                double eu = 1.5 * (cm - 15.5) * 1.5;
                return "Size " + Ui.fmt(v) + " " + f.toUpperCase() + " ≈\n"
                        + "US:   " + Ui.fmt(Math.round(us * 2) / 2.0) + "\n"
                        + "UK:   " + Ui.fmt(Math.round(uk * 2) / 2.0) + "\n"
                        + "EU:   " + Ui.fmt(Math.round(eu)) + "\n"
                        + "CM:   " + Ui.fmt(cm, 1) + " cm";
            }
        });

        // Clothing Size
        Compute.reg("Clothing Size", new String[][]{
                ComputeText.fl("CHEST CM", "Chest in cm"),
                ComputeText.fl("WAIST CM", "Waist in cm")
        }, "CONVERT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double chest = Compute.d(in, "f0");
                double waist = Compute.d(in, "f1");
                String intl;
                if (chest < 84) intl = "XS";
                else if (chest < 90) intl = "S";
                else if (chest < 96) intl = "M";
                else if (chest < 104) intl = "L";
                else if (chest < 112) intl = "XL";
                else if (chest < 122) intl = "XXL";
                else intl = "3XL";
                int eu = (int) Math.round((chest - 80) / 6.0) * 2 + 38;
                int us = eu - 34;
                int uk = eu - 36;
                return "Chest " + Ui.fmt(chest) + " cm / Waist " + Ui.fmt(waist) + " cm\n\n"
                        + "International: " + intl + "\nEU: " + eu + "\nUS: " + us + "\nUK: " + uk;
            }
        });

        // Roman Numerals
        Compute.reg("Roman Numerals", new String[][]{
                ComputeText.fl("INPUT", "Number (1-3999) or Roman numeral (e.g. 2026 or MMXXVI)")
        }, "CONVERT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String v = Compute.s(in, "f0").toUpperCase();
                if (v.matches("[0-9]+")) {
                    int n = Compute.i(in, "f0");
                    if (n < 1 || n > 3999) throw new Exception("Number must be 1-3999");
                    return v + "  =  " + toRoman(n);
                }
                if (!v.matches("[MDCLXVI]+")) throw new Exception("Enter a number or Roman numeral");
                int n = fromRoman(v);
                if (n < 1 || n > 3999) throw new Exception("Out of range");
                return v + "  =  " + n + "\nCheck: " + toRoman(n);
            }
        });

        // Number Base
        Compute.reg("Number Base", new String[][]{
                ComputeText.fl("VALUE", "Enter value"),
                ComputeText.fl("FROM BASE", "2 / 8 / 10 / 16"),
                ComputeText.fl("TO BASE", "2 / 8 / 10 / 16")
        }, "CONVERT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String v = Compute.s(in, "f0");
                int fb = Compute.i(in, "f1");
                int tb = Compute.i(in, "f2");
                long n = Long.parseLong(v.replace(" ", ""), fb);
                return v.toUpperCase() + " (base " + fb + ")  =\n\n"
                        + "BIN:  " + Long.toBinaryString(n) + "\n"
                        + "OCT:  " + Long.toOctalString(n) + "\n"
                        + "DEC:  " + n + "\n"
                        + "HEX:  " + Long.toHexString(n).toUpperCase() + "\n\n"
                        + "Base " + tb + ": " + Long.toString(n, tb).toUpperCase();
            }
        });

        // Timestamp Converter
        Compute.reg("Timestamp Converter", new String[][]{
                ComputeText.fl("INPUT", "Unix timestamp (s or ms) OR yyyy-MM-dd HH:mm:ss")
        }, "CONVERT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String v = Compute.s(in, "f0").replace("T", " ");
                if (v.matches("[0-9]+")) {
                    long ts = Long.parseLong(v);
                    if (v.length() > 11) ts /= 1000;
                    Date d = new Date(ts * 1000L);
                    java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                    f.setTimeZone(TimeZone.getTimeZone("UTC"));
                    return "Timestamp: " + ts + "\nUTC: " + f.format(d) + "Z\n"
                            + "Local: " + java.text.DateFormat.getDateTimeInstance().format(d) + "\n"
                            + "Day:  " + new java.text.SimpleDateFormat("EEEE", Locale.US).format(d);
                }
                java.text.SimpleDateFormat p = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                Date d = p.parse(v);
                java.text.SimpleDateFormat iso = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                iso.setTimeZone(TimeZone.getTimeZone("UTC"));
                return "Date: " + v + "\nUnix (s): " + d.getTime() / 1000 + "\nUnix (ms): " + d.getTime()
                        + "\nISO: " + iso.format(d);
            }
        });

        // Timezone Converter
        Compute.reg("Timezone Converter", new String[][]{
                ComputeText.fl("DATETIME", "yyyy-MM-dd HH:mm (your local time)"),
                ComputeText.fl("FROM TZ", "e.g. Asia/Yangon"),
                ComputeText.fl("TO TZ", "e.g. Europe/Paris")
        }, "CONVERT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String dt = Compute.s(in, "f0");
                String ftz = Compute.s(in, "f1");
                String ttz = Compute.s(in, "f2");
                java.text.SimpleDateFormat p = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                p.setTimeZone(TimeZone.getTimeZone(ftz));
                Date d = p.parse(dt);
                java.text.SimpleDateFormat f1 = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                f1.setTimeZone(TimeZone.getTimeZone(ftz));
                java.text.SimpleDateFormat f2 = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                f2.setTimeZone(TimeZone.getTimeZone(ttz));
                return f1.format(d) + "  [" + ftz + "]\n     =\n" + f2.format(d) + "  [" + ttz + "]";
            }
        });

        // Text -> Binary
        Compute.reg("Text → Binary", new String[][]{ComputeText.ml("TEXT", "Type text")}, "CONVERT", new Compute.Fn() {
            public String run(Map<String, String> in) {
                byte[] bs = ComputeText.nv(in.get("f0")).getBytes();
                StringBuilder sb = new StringBuilder();
                for (byte b : bs) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
                }
                return sb.toString();
            }
        });

        // Binary -> Text
        Compute.reg("Binary → Text", new String[][]{ComputeText.ml("BINARY", "e.g. 01001000 01101001")}, "DECODE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String v = ComputeText.nv(in.get("f0")).replace(" ", "").replace("\n", "");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i + 8 <= v.length(); i += 8) {
                    sb.append((char) Integer.parseInt(v.substring(i, i + 8), 2));
                }
                return sb.toString();
            }
        });

        // Text -> Hex
        Compute.reg("Text → Hex", new String[][]{ComputeText.ml("TEXT", "Type text")}, "CONVERT", new Compute.Fn() {
            public String run(Map<String, String> in) {
                byte[] bs = ComputeText.nv(in.get("f0")).getBytes();
                StringBuilder sb = new StringBuilder();
                for (byte b : bs) sb.append(String.format("%02X", b));
                return sb.toString();
            }
        });

        // Morse Code
        Compute.reg("Morse Code", new String[][]{
                ComputeText.fl("MODE", "encode / decode"),
                ComputeText.ml("INPUT", "Text or morse (.... ..)")
        }, "RUN", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String m = Compute.s(in, "f0").toLowerCase();
                String v = ComputeText.nv(in.get("f1")).toUpperCase();
                String[] code = {".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..", ".---", "-.-",
                        ".-..", "--", "-.", "---", ".--.", "--.-", ".-.", "...", "-", "..-", "...-", ".--",
                        "-..-", "-.--", "--..", "-----", ".----", "..---", "...--", "....-", ".....",
                        "-....", "--...", "---..", "----."};
                StringBuilder sb = new StringBuilder();
                if (m.equals("encode")) {
                    for (char ch : v.toCharArray()) {
                        if (ch >= 'A' && ch <= 'Z') sb.append(code[ch - 'A']).append(' ');
                        else if (ch >= '0' && ch <= '9') sb.append(code[ch - '0' + 26]).append(' ');
                        else if (ch == ' ') sb.append("/ ");
                        else sb.append(ch);
                    }
                } else {
                    for (String tok : v.trim().split("\\s+/\\s+|\\s+")) {
                        if (tok.equals("/")) {
                            sb.append(' ');
                            continue;
                        }
                        boolean found = false;
                        for (int i = 0; i < code.length; i++) {
                            if (code[i].equals(tok)) {
                                sb.append(i < 26 ? (char) ('A' + i) : (char) ('0' + i - 26));
                                found = true;
                                break;
                            }
                        }
                        if (!found && tok.length() > 0) sb.append('?');
                    }
                }
                return sb.toString().trim();
            }
        });
    }

    static String toRoman(int n) {
        int[] val = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] sym = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < val.length; i++) {
            while (n >= val[i]) {
                sb.append(sym[i]);
                n -= val[i];
            }
        }
        return sb.toString();
    }

    static int fromRoman(String r) {
        java.util.HashMap<Character, Integer> m = new java.util.HashMap<>();
        m.put('I', 1);
        m.put('V', 5);
        m.put('X', 10);
        m.put('L', 50);
        m.put('C', 100);
        m.put('D', 500);
        m.put('M', 1000);
        int total = 0;
        for (int i = 0; i < r.length(); i++) {
            int c = m.containsKey(r.charAt(i)) ? m.get(r.charAt(i)) : 0;
            int n = i + 1 < r.length() && m.containsKey(r.charAt(i + 1)) ? m.get(r.charAt(i + 1)) : 0;
            total += c < n ? -c : c;
        }
        return total;
    }
}
