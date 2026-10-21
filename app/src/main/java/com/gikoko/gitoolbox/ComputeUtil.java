package com.gikoko.gitoolbox;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Utility tools (8). */
final class ComputeUtil {

    private ComputeUtil() {
    }

    static final Random RNG = new Random();

    static String[] fl(String l, String h) {
        return new String[]{l, h};
    }

    static String[] ml(String l, String h) {
        return new String[]{l, h, "multi"};
    }

    static void register() {

        // Calendar (text grid)
        Compute.reg("Calendar", new String[][]{fl("MONTH", "1-12 (blank = this month)"),
                fl("YEAR", "e.g. 2026 (blank = this year)")}, "SHOW", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                Calendar now = Calendar.getInstance();
                int m = Compute.s(in, "f0").length() == 0 ? now.get(Calendar.MONTH) + 1 : Compute.i(in, "f0");
                int y = Compute.s(in, "f1").length() == 0 ? now.get(Calendar.YEAR) : Compute.i(in, "f1");
                if (m < 1 || m > 12) throw new Exception("Month must be 1-12");
                Calendar c = new GregorianCalendar(y, m - 1, 1);
                String[] names = {"January", "February", "March", "April", "May", "June", "July",
                        "August", "September", "October", "November", "December"};
                StringBuilder sb = new StringBuilder();
                sb.append("     ").append(names[m - 1]).append(' ').append(y).append("\n\n");
                sb.append("Mo Tu We Th Fr Sa Su\n");
                int first = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Monday-based
                int days = c.getActualMaximum(Calendar.DAY_OF_MONTH);
                int today = (m == now.get(Calendar.MONTH) + 1 && y == now.get(Calendar.YEAR)) ? now.get(Calendar.DAY_OF_MONTH) : 0;
                int col = 0;
                for (int i = 0; i < first; i++) {
                    sb.append("   ");
                    col++;
                }
                for (int d = 1; d <= days; d++) {
                    String cell = d < 10 ? " " + d : String.valueOf(d);
                    sb.append(d == today ? "[" + cell + "]" : cell);
                    sb.append(d == today ? "" : " ");
                    col++;
                    if (col == 7 && d < days) {
                        sb.append('\n');
                        col = 0;
                    }
                }
                sb.append("\n\nToday: ").append(new java.text.SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).format(now.getTime()));
                return sb.toString();
            }
        });

        Compute.reg("Dice Roller", new String[][]{fl("DICE", "e.g. 2d6, 1d20, 3d8 (blank = 1d6)")}, "ROLL", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String v = Compute.s(in, "f0").toLowerCase(Locale.US).replace(" ", "");
                if (v.length() == 0) v = "1d6";
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d*)d(\\d+)").matcher(v);
                if (!m.matches()) throw new Exception("Use format like 2d6");
                int count = m.group(1).length() == 0 ? 1 : Integer.parseInt(m.group(1));
                int sides = Integer.parseInt(m.group(2));
                if (count < 1 || count > 20) throw new Exception("1-20 dice");
                if (sides < 2 || sides > 1000) throw new Exception("2-1000 sides");
                StringBuilder sb = new StringBuilder();
                int total = 0;
                for (int i = 0; i < count; i++) {
                    int r = RNG.nextInt(sides) + 1;
                    total += r;
                    sb.append(r);
                    if (i < count - 1) sb.append(" + ");
                }
                return "🎲 " + v + " →  " + sb + (count > 1 ? "  =  " + total : "");
            }
        });

        Compute.reg("Coin Flip", new String[][]{fl("COUNT", "how many flips (blank = 1)")}, "FLIP", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                int c = Compute.s(in, "f0").length() == 0 ? 1 : Compute.i(in, "f0");
                if (c < 1 || c > 1000) throw new Exception("1-1000");
                if (c == 1) return "🪙 " + (RNG.nextBoolean() ? "HEADS" : "TAILS");
                int h = 0, t = 0;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < c; i++) {
                    boolean heads = RNG.nextBoolean();
                    if (heads) h++;
                    else t++;
                    sb.append(heads ? 'H' : 'T');
                }
                return sb + "\n\nHeads: " + h + "  (" + Ui.fmt(h * 100.0 / c, 1) + "%)\nTails: " + t + "  (" + Ui.fmt(t * 100.0 / c, 1) + "%)";
            }
        });

        Compute.reg("Random Picker", new String[][]{ml("OPTIONS", "one option per line")}, "PICK", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String[] opts = ComputeText.nv(in.get("f0")).split("\n");
                java.util.ArrayList<String> valid = new java.util.ArrayList<>();
                for (String o : opts) if (o.trim().length() > 0) valid.add(o.trim());
                if (valid.size() < 2) throw new IllegalArgumentException("Enter at least 2 options");
                return "🎯 " + valid.get(RNG.nextInt(valid.size()));
            }
        });

        Compute.reg("Decision Wheel", new String[][]{ml("OPTIONS", "one option per line")}, "SPIN", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String[] opts = ComputeText.nv(in.get("f0")).split("\n");
                java.util.ArrayList<String> valid = new java.util.ArrayList<>();
                for (String o : opts) if (o.trim().length() > 0) valid.add(o.trim());
                if (valid.size() < 2) throw new IllegalArgumentException("Enter at least 2 options");
                return "🎡 Wheel result: " + valid.get(RNG.nextInt(valid.size()))
                        + "\n\n(Full spinning wheel animation ships in the next update — this picks instantly.)";
            }
        });

        Compute.reg("Countdown Days", new String[][]{fl("EVENT", "event name"),
                fl("DATE", "yyyy-MM-dd")}, "COUNT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String ev = Compute.s(in, "f0");
                if (ev.length() == 0) ev = "The event";
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
                Calendar target = new GregorianCalendar();
                target.setTime(f.parse(Compute.s(in, "f1")));
                target.set(Calendar.HOUR_OF_DAY, 0);
                Calendar now = new GregorianCalendar();
                now.set(Calendar.HOUR_OF_DAY, 0);
                now.set(Calendar.MINUTE, 0);
                now.set(Calendar.SECOND, 0);
                now.set(Calendar.MILLISECOND, 0);
                long days = (target.getTimeInMillis() - now.getTimeInMillis()) / 86400000L;
                if (days == 0) return "⏰ " + ev + " is TODAY!";
                if (days > 0) return "⏰ " + days + (days == 1 ? " day" : " days") + " until " + ev + "!";
                return "✅ " + ev + " was " + (-days) + " days ago.";
            }
        });

        Compute.reg("Name Picker", new String[][]{ml("NAMES", "one name per line"),
                fl("WINNERS", "how many (blank = 1)")}, "DRAW", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String[] opts = ComputeText.nv(in.get("f0")).split("\n");
                java.util.ArrayList<String> valid = new java.util.ArrayList<>();
                for (String o : opts) if (o.trim().length() > 0) valid.add(o.trim());
                if (valid.size() < 1) throw new IllegalArgumentException("Enter names");
                int w = Compute.s(in, "f1").length() == 0 ? 1 : Compute.i(in, "f1");
                if (w < 1 || w > valid.size()) throw new Exception("Winners must be 1-" + valid.size());
                java.util.Collections.shuffle(valid, RNG);
                StringBuilder sb = new StringBuilder("🏆 Winners:\n");
                for (int i = 0; i < w; i++) sb.append(i + 1).append(". ").append(valid.get(i)).append('\n');
                return sb.toString().trim();
            }
        });

        Compute.reg("Price Compare", new String[][]{fl("PRICE A", "price of item A"),
                fl("SIZE A", "size/weight of A, e.g. 500"), fl("UNIT A", "g / ml / kg (blank = g)"),
                fl("PRICE B", "price of item B"), fl("SIZE B", "size of B, e.g. 750"), fl("UNIT B", "blank = g)")}, "COMPARE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double pa = Compute.d(in, "f0"), sa = Compute.d(in, "f1");
                double pb = Compute.d(in, "f3"), sb2 = Compute.d(in, "f4");
                String ua = Compute.s(in, "f2").length() == 0 ? "g" : Compute.s(in, "f2");
                String ub = Compute.s(in, "f5").length() == 0 ? "g" : Compute.s(in, "f5");
                // normalize kg->g, l->ml
                double na = ua.startsWith("k") ? sa * 1000 : sa;
                double nb = ub.startsWith("k") ? sb2 * 1000 : sb2;
                double ra = pa / na, rb = pb / nb;
                String winner = ra <= rb ? "A" : "B";
                return "Per unit price:\n"
                        + "A: " + Ui.fmt(pa, 2) + " / " + Ui.fmt(na, 0) + " = " + Ui.fmt(ra * 100, 2) + " per 100\n"
                        + "B: " + Ui.fmt(pb, 2) + " / " + Ui.fmt(nb, 0) + " = " + Ui.fmt(rb * 100, 2) + " per 100\n\n"
                        + "✅ Item " + winner + " is the better deal."
                        + "\nYou save " + Ui.fmt(Math.abs(ra - rb) * Math.max(na, nb) / 100, 2) + " per " + Math.max(na, nb) + " units.";
            }
        });
    }
}
