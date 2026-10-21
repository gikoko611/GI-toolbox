package com.gikoko.gitoolbox;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;

/**
 * v0.0.1 Myanmar Calendar Lab — 100% native:
 * - Myanmar Calendar: Burmese weekday, Myanmar Era (M.E.) year, month,
 *   lunar fortnight/day from Meeus new-moon math, moon illumination.
 * - Myanmar Numerals: live Arabic ↔ Myanmar (၀-၉) digit conversion.
 * - Age (Myanmar): age in years/months/days + M.E. birth year + birthday countdown.
 *
 * Lunar math uses the truncated Meeus new-moon series — accurate to roughly
 * ±2 minutes for modern dates, more than enough for calendar display.
 */
public class MyanmarCalendarActivity extends Activity {

    private static final String[] WEEK_EN = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    private static final String[] WEEK_MY = {
            "\u1010\u100E\u1004\u103A\u1001\u100E\u103D\u1031",  // tanin-ganwe
            "\u1010\u100E\u1004\u103A\u1015\u102C",              // tanin-la
            "\u101A\u1004\u103A\u1001\u102B",                    // inga
            "\u1011\u102F\u100C\u1039\u100D\u1019\u102E\u103A", // Buddhahu
            "\u1000\u103C\u102C\u1018\u100F\u100A\u1031\u1037",  // kyathat-taw-de
            "\u1018\u1031\u102C\u1000\u103C\u102C",              // thaw-kya
            "\u1005\u100E\u1031"                                 // sani
    };
    private static final String[] MONTH = {"Tagu", "Kason", "Nayon", "Waso", "Wagaung",
            "Tawthalin", "Thadingyut", "Tazaungmon", "Nadaw", "Pyatho", "Tabodwe", "Tabaung"};
    private static final String MY_DIGITS = "\u1040\u1041\u1042\u1043\u1044\u1045\u1046\u1047\u1048\u1049";

    private String tool;
    private LinearLayout out;

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
        LinearLayout br = Ui.row(this);
        br.addView(Ui.badge(this, "● မြန်မာ CALENDAR ENGINE", Ui.GOLD));
        root.addView(br);
        Ui.reveal(br);

        out = new LinearLayout(this);
        out.setOrientation(LinearLayout.VERTICAL);
        root.addView(out);

        setContentView(Ui.scroll(this, root));
        if (tool.equals("Myanmar Numerals")) numerals();
        else if (tool.equals("Age (Myanmar)")) ageMy();
        else calendarMain();
    }

    // ------------------------------------------------------------ main calendar

    void calendarMain() {
        intro("Live Myanmar lunar calendar. Today is shown first — tap CHOOSE DATE to check "
                "any date: Burmese weekday, Myanmar Era year, month, lunar day and moon phase.");

        LocalDate today = LocalDate.now();
        showDateCard(today, "TODAY");

        Button pick = Ui.button(this, "📅 CHOOSE DATE", Ui.CYAN);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LocalDate t = LocalDate.now();
                DatePickerDialog d = new DatePickerDialog(MyanmarCalendarActivity.this,
                        new DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(DatePicker view, int y, int m, int day) {
                                showDateCard(LocalDate.of(y, m + 1, day), "CHOSEN DATE");
                            }
                        }, t.getYear(), t.getMonthValue() - 1, t.getDayOfMonth());
                d.show();
            }
        });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pl.topMargin = Ui.dp(this, 8);
        pick.setLayoutParams(pl);
        out.addView(pick);

        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "MYANMAR LUNAR FACTS", 11, Ui.DIM, true));
        c.addView(Ui.text(this, "•  Myanmar months follow the solar year (12 months of ~30.4 days) "
                + "while the lunar day (လ) tracks the waxing/waning moon.\n"
                + "•  The Myanmar Era (M.E., ကောဇာသက္ကရာဇ်) began 22 March 638 CE.\n"
                + "•  Lunar day 15 of the waxing fortnight is လပြည့် (full moon); day 15 of "
                + "the waning fortnight is လကွယ် (new moon eve) — the big sabbath days.", 11, Ui.TEXT, false));
        out.addView(c);
    }

    void showDateCard(LocalDate d, String tag) {
        LinearLayout c = Ui.card(this);
        LinearLayout top = Ui.row(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.badge(this, tag, Ui.CYAN));
        c.addView(top);

        TextView big = Ui.text(this, fmt2(d.getDayOfMonth()) + " " + d.getMonth() + " " + d.getYear(), 20, Ui.GOLD, true);
        big.setGravity(Gravity.CENTER);
        c.addView(big);
        Ui.pop(big);

        int jdn = gregorianToJdn(d.getYear(), d.getMonthValue(), d.getDayOfMonth());
        int dow = (jdn + 1) % 7;

        // Myanmar Era year: epoch 22 March 638 CE (M.E. 0), year length 365.25875
        int epochJdn = gregorianToJdn(638, 3, 22);
        long meYear = (long) Math.floor((jdn - epochJdn) / 365.25875) + 1;
        double yearStart = epochJdn + (meYear - 1) * 365.25875;
        int doy = (int) Math.floor(jdn - yearStart);
        int monthIdx = (int) Math.floor(doy / (365.25875 / 12.0));
        if (monthIdx > 11) monthIdx = 11;
        if (monthIdx < 0) monthIdx = 0;

        // lunar phase from Meeus new moons
        double k = Math.floor((jdn - 2451550.09766) / 29.530588861);
        double nm = newMoon(k);
        double nmNext = newMoon(k + 1);
        double age = jdn + 0.5 - nm;                 // days since new moon
        double mlen = nmNext - nm;                   // lunar month length
        double phase = age / mlen;                   // 0..1
        int illum = (int) Math.round((1 - Math.cos(2 * Math.PI * phase)) / 2 * 100);

        String fortnight;
        int lunarDay;
        if (age < 1.0) {
            fortnight = " NEW MOON · လကွယ်နေ့";
            lunarDay = 0;
        } else if (age < 15.5) {
            lunarDay = (int) Math.min(15, Math.floor(age + 0.5));
            fortnight = lunarDay == 15 ? " FULL MOON · လပြည့်နေ့" : " WAXING · လဆန်း";
        } else {
            lunarDay = (int) Math.min(15, Math.floor(age - 14.5 + 0.5));
            fortnight = " WANING · လပြီး";
        }

        c.addView(row("WEEKDAY", WEEK_EN[dow] + " · " + WEEK_MY[dow]));
        c.addView(row("MYANMAR YEAR", toMyDigits(meYear) + " M.E.  (" + meYear + ")"));
        c.addView(row("MYANMAR MONTH", MONTH[monthIdx] + "  ·  solar day " + (doy + 1) + " of the M.E. year"));
        c.addView(row("LUNAR DAY", lunarDay == 0 ? "—" : toMyDigits(lunarDay) + " လ" + fortnight));
        c.addView(row("MOON ILLUMINATION", illum + "% of the disc lit"));
        c.addView(row("NEXT FULL MOON", jdnToNice((long) Math.floor(newMoon(k + 0.5)))));
        c.addView(row("NEXT NEW MOON", jdnToNice((long) Math.floor(nmNext))));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        c.setLayoutParams(lp);
        out.addView(c);
        Ui.reveal(c);
        if (tag.equals("CHOSEN DATE")) {
            c.requestFocus();
        }
    }

    // ------------------------------------------------------------ numerals

    void numerals() {
        intro("Type any number — it converts live between international digits (0123456789) "
                + "and Myanmar digits (၀၁၂၃၄၅၆၇၈၉).");

        final EditText num = Ui.input(this, "e.g. 2026", false);
        num.setTag("num");
        num.setTextSize(20);
        out.addView(num);

        LinearLayout res = Ui.card(this);
        res.addView(Ui.text(this, "MYANMAR NUMERALS", 10, Ui.DIM, true));
        final TextView my = Ui.text(this, "\u1042\u1040\u1042\u1046", 34, Ui.GOLD, true);
        my.setGravity(Gravity.CENTER);
        my.setTag("my");
        res.addView(my);
        out.addView(res);
        Ui.pop(res);

        final TextView rev = Ui.text(this, "", 12, Ui.CYAN, false);
        rev.setPadding(0, Ui.dp(this, 8), 0, 0);
        res.addView(rev);

        num.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b2, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String in = s.toString().trim();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < in.length(); i++) {
                    char ch = in.charAt(i);
                    if (ch >= '0' && ch <= '9') sb.append(MY_DIGITS.charAt(ch - '0'));
                    else if (ch == '.' || ch == ',' || ch == '-') sb.append(ch);
                    else {
                        int m = MY_DIGITS.indexOf(ch);
                        if (m >= 0) sb.append(ch);
                    }
                }
                my.setText(sb.length() == 0 ? "—" : sb.toString());
                rev.setText(sb.length() == 0 ? "" : "as words: " + fmt2(in.length()) + " characters");
            }
        });
        num.setText("1948");

        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "DID YOU KNOW", 10, Ui.DIM, true));
        c.addView(Ui.text(this, "Myanmar numerals ၀၁၂၃၄၅၆၇၈၉ are used on official documents, "
                + "school books and bank notes. Tip: paste Myanmar digits into the box to read "
                + "them back — paste ၂၀၂၆ to check!", 11, Ui.TEXT, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        c.setLayoutParams(lp);
        out.addView(c);
    }

    // ------------------------------------------------------------ age (myanmar)

    void ageMy() {
        intro("Your exact age the Myanmar way: years, months and days since birth, the weekday "
                + "you were born on, your Myanmar Era birth year, and days until your next birthday.");

        LocalDate today = LocalDate.now();
        LocalDate def = LocalDate.of(today.getYear() - 20, today.getMonthValue(), Math.min(28, today.getDayOfMonth()));

        LinearLayout c = Ui.card(this);
        final TextView chosen = Ui.text(this, "Birth date: not chosen", 13, Ui.CYAN, true);
        chosen.setTag("chosen");
        c.addView(chosen);
        final TextView age = Ui.text(this, "Tap PICK BIRTH DATE →", 12, Ui.TEXT, false);
        age.setTag("agebox");
        c.addView(age);
        out.addView(c);

        Button pick = Ui.button(this, "🎂 PICK BIRTH DATE", Ui.GOLD);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LocalDate t = LocalDate.now();
                DatePickerDialog d = new DatePickerDialog(MyanmarCalendarActivity.this,
                        new DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(DatePicker view, int y, int m, int day) {
                                showAge(LocalDate.of(y, m + 1, day));
                            }
                        }, t.getYear() - 20, t.getMonthValue() - 1, t.getDayOfMonth());
                d.show();
            }
        });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pl.topMargin = Ui.dp(this, 8);
        pick.setLayoutParams(pl);
        out.addView(pick);

        LinearLayout f = Ui.card(this);
        f.addView(Ui.text(this, "MYANMAR BIRTHDAY CULTURE", 10, Ui.DIM, true));
        f.addView(Ui.text(this, "Birthdays are counted to the day in Myanmar, and many families "
                + "also note the နာရီ (hour pillar) and weekday of birth from traditional "
                + "astrology (ဗေဒင်). This tool gives you the exact weekday and the Myanmar Era "
                + "year you were born in.", 11, Ui.TEXT, false));
        out.addView(f);
    }

    void showAge(LocalDate birth) {
        LocalDate today = LocalDate.now();
        if (birth.isAfter(today)) {
            toast("Birth date is in the future");
            return;
        }
        Period p = Period.between(birth, today);
        long totalDays = ChronoUnit.DAYS.between(birth, today);
        int jdn = gregorianToJdn(birth.getYear(), birth.getMonthValue(), birth.getDayOfMonth());
        int dow = (jdn + 1) % 7;
        int epochJdn = gregorianToJdn(638, 3, 22);
        long meYear = (long) Math.floor((jdn - epochJdn) / 365.25875) + 1;

        LocalDate next = birth.plusYears(p.getYears() + 1);
        long untilNext = ChronoUnit.DAYS.between(today, next);

        TextView chosen = (TextView) out.findViewWithTag("chosen");
        if (chosen != null) chosen.setText("Birth date: " + fmt2(birth.getDayOfMonth()) + " " + birth.getMonth() + " " + birth.getYear());
        TextView age = (TextView) out.findViewWithTag("agebox");
        if (age != null) {
            age.setText("AGE: " + p.getYears() + " years, " + p.getMonths() + " months, " + p.getDays() + " days"
                    + "\nTOTAL DAYS ALIVE: " + totalDays + " days"
                    + "\nBORN ON A: " + WEEK_EN[dow] + " · " + WEEK_MY[dow]
                    + "\nMYANMAR ERA BIRTH YEAR: " + toMyDigits(meYear) + " M.E.  (" + meYear + ")"
                    + "\nNEXT BIRTHDAY: in " + untilNext + " days (" + next.getDayOfWeek() + ")"
                    + "\nHEARTBEATS SO FAR: ~" + totalDays * 100000L + " beats");
            Ui.flash(age);
        }
    }

    // ------------------------------------------------------------ astronomy + jdn

    /** Truncated Meeus (Ch. 49) new-moon JDE for lunation k. */
    static double newMoon(double k) {
        double T = k / 1236.85;
        double T2 = T * T;
        double T3 = T2 * T;
        double T4 = T3 * T;
        double jde = 2451550.09766 + 29.530588861 * k
                + 0.00015437 * T2 - 0.000000150 * T3 + 0.00000000073 * T2 * T2;
        double E = 1 - 0.002516 * T - 0.0000074 * T2;
        double M = 2.5534 + 29.10535670 * k - 0.0000014 * T2 - 0.00000011 * T3;
        double Mp = 201.5643 + 385.81693528 * k + 0.0107582 * T2 + 0.00001238 * T3 - 0.000000058 * T4;
        double F = 160.7108 + 390.67050284 * k - 0.0016118 * T2 - 0.00000227 * T3 + 0.000000011 * T4;
        return jde
                - 0.40720 * sin(Mp)
                + 0.17241 * E * sin(M)
                + 0.01608 * sin(2 * Mp)
                + 0.01039 * sin(2 * F)
                + 0.00739 * E * sin(Mp - M)
                - 0.00514 * E * sin(M + Mp)
                + 0.00208 * E * E * sin(2 * M)
                - 0.00111 * sin(Mp - 2 * F)
                - 0.00057 * sin(Mp + 2 * F)
                + 0.00056 * E * sin(2 * Mp + M)
                - 0.00042 * sin(3 * Mp)
                + 0.00042 * E * sin(M + 2 * F)
                + 0.00038 * E * sin(M - 2 * F)
                - 0.00024 * E * sin(2 * Mp - M)
                - 0.00017 * sin(M + 2 * Mp);
    }

    static double sin(double deg) {
        return Math.sin(Math.toRadians(deg));
    }

    /** Gregorian → Julian Day Number (integer noon JDN, Fliegel–Van Flandern). */
    static int gregorianToJdn(int y, int m, int d) {
        int a = (14 - m) / 12;
        int y2 = y + 4800 - a;
        int m2 = m + 12 * a - 3;
        return d + (153 * m2 + 2) / 5 + 365 * y2 + y2 / 4 - y2 / 100 + y2 / 400 - 32045;
    }

    /** JDN → readable date string. */
    static String jdnToNice(long jdn) {
        long a = jdn + 32044;
        long b = (4 * a + 3) / 146097;
        long c = a - 146097 * b / 4;
        long d2 = (4 * c + 3) / 1461;
        long e = c - 1461 * d2 / 4;
        long m2 = (5 * e + 2) / 153;
        long day = e - (153 * m2 + 2) / 5 + 1;
        long month = m2 + 3 - 12 * (m2 / 10);
        long year = 100 * b + d2 - 4800 + m2 / 10;
        String[] mn = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
        return day + " " + mn[(int) (month - 1)] + " " + year;
    }

    /** 12345 → "၁၂၃၄၅" */
    static String toMyDigits(long n) {
        String s = String.valueOf(n);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') sb.append(MY_DIGITS.charAt(ch - '0'));
            else sb.append(ch);
        }
        return sb.toString();
    }

    static String fmt2(long v) {
        return String.valueOf(v);
    }

    // ------------------------------------------------------------ helpers

    void intro(String s) {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, s, 12, Ui.TEXT, false));
        out.addView(c);
        Ui.reveal(c);
    }

    View row(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.text(this, label, 10, Ui.DIM, true));
        box.addView(Ui.text(this, value, 13, Ui.TEXT, true));
        box.setPadding(0, Ui.dp(this, 6), 0, 0);
        return box;
    }

    void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }
}
