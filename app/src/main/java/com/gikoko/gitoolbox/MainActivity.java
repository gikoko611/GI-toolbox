package com.gikoko.gitoolbox;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Splash + home (search + 12 category grid) + all tools + about. 100% native, no WebView. */
public class MainActivity extends Activity {

    private int page = 0; // 0 home, 1 all, 2 about
    private LinearLayout root;
    private EditText search;
    private LinearLayout resultsBox;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        android.content.SharedPreferences prefs = getSharedPreferences("gitoolbox_lists", android.content.Context.MODE_PRIVATE);
        if (prefs.getString("applock_pin", "").length() > 0) {
            showGate(prefs);
        } else {
            showSplash();
        }
    }

    /** PIN gate shown at launch when App Lock is enabled. */
    private void showGate(final android.content.SharedPreferences prefs) {
        LinearLayout l = Ui.screen(this);
        l.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout c = Ui.card(this);
        TextView lock = Ui.text(this, "\uD83D\uDD12", 44, Ui.GOLD, false);
        lock.setGravity(Gravity.CENTER);
        c.addView(lock);
        TextView t1 = Ui.gold(this, "APP LOCKED", 18);
        t1.setGravity(Gravity.CENTER);
        c.addView(t1);
        TextView t2 = Ui.text(this, "Enter your PIN to unlock GIToolbox", 12, Ui.DIM, false);
        t2.setGravity(Gravity.CENTER);
        c.addView(t2);
        l.addView(c);
        Ui.pop(c);

        final EditText pin = Ui.input(this, "PIN", false);
        pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.topMargin = Ui.dp(this, 12);
        pin.setLayoutParams(plp);
        l.addView(pin);

        Button unlock = Ui.button(this, "🔓 UNLOCK", Ui.GREEN);
        unlock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String p = pin.getText().toString().trim();
                if (p.equals(prefs.getString("applock_pin", ""))) {
                    pin.setText("");
                    showSplash();
                } else {
                    pin.setText("");
                    android.widget.Toast.makeText(MainActivity.this, "Wrong PIN", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ulp.topMargin = Ui.dp(this, 12);
        unlock.setLayoutParams(ulp);
        l.addView(unlock);

        setContentView(l);
    }

    private void showSplash() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(Ui.BG);
        int p = Ui.dp(this, 40);
        l.setPadding(p, p, p, p);

        TextView gi = new TextView(this);
        gi.setText("GI");
        gi.setTextSize(64);
        gi.setTextColor(Ui.GOLD);
        gi.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
        gi.setGravity(Gravity.CENTER);
        gi.setLetterSpacing(0.1f);
        l.addView(gi);
        Ui.pop(gi);

        TextView name = Ui.gold(this, "GIToolbox", 30);
        name.setGravity(Gravity.CENTER);
        l.addView(name);
        Ui.reveal(name, 200);

        TextView ver = Ui.badge(this, "● v3.0 · NEBULA EDITION", Ui.GREEN);
        ver.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = Ui.dp(this, 10);
        ver.setLayoutParams(vlp);
        l.addView(ver);

        TextView sub = Ui.text(this, "ALL-IN-ONE · 325 FEATURES · NATIVE ENGINE", 12, Ui.DIM, false);
        sub.setGravity(Gravity.CENTER);
        sub.setLetterSpacing(0.2f);
        l.addView(sub);

        TextView bar = Ui.text(this, "████████████████████", 16, Ui.GREEN, false);
        bar.setGravity(Gravity.CENTER);
        l.addView(bar);
        Ui.breathe(bar);

        TextView brand = Ui.gold(this, "POWERED BY I KO KO", 14);
        brand.setGravity(Gravity.CENTER);
        l.addView(brand);

        setContentView(l);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                showHome();
            }
        }, 1800);
    }

    private void showHome() {
        page = 0;
        root = Ui.screen(this);

        // header
        LinearLayout head = Ui.row(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView gi = Ui.text(this, "GI", 30, Ui.GOLD, true);
        gi.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
        head.addView(gi);
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        hlp.leftMargin = Ui.dp(this, 10);
        ht.setLayoutParams(hlp);
        ht.addView(Ui.gold(this, "GIToolbox", 20));
        ht.addView(Ui.text(this, "325 FEATURES · NATIVE v3.0", 11, Ui.DIM, false));
        head.addView(ht);
        TextView ver = Ui.text(this, "v3.0", 11, Ui.GOLD, true);
        head.addView(ver);
        root.addView(head);
        Ui.reveal(head);

        // search
        search = Ui.input(this, "Search 325 tools...", false);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int c, int d) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onSearch(s.toString());
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(this, 14);
        search.setLayoutParams(slp);
        root.addView(search);

        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultsBox);

        // banner
        LinearLayout banner = Ui.card(this);
        LinearLayout bTop = Ui.row(this);
        bTop.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams btlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        TextView bTitle = Ui.gold(this, "⚡ ALL-IN-ONE TOOLBOX", 13);
        bTitle.setLayoutParams(btlp);
        bTop.addView(bTitle);
        bTop.addView(Ui.badge(this, "● 325 TOOLS LIVE", Ui.GREEN));
        banner.addView(bTop);
        TextView b2 = Ui.text(this, "Nebula Edition · Green • Gold • Dark · POWERED BY I KO KO", 10, Ui.TEXT, false);
        b2.setPadding(0, Ui.dp(this, 4), 0, 0);
        banner.addView(b2);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 12);
        banner.setLayoutParams(blp);
        root.addView(banner);
        Ui.reveal(banner, 80);

        TextView secl = Ui.text(this, "CATEGORIES · " + Catalog.CATS.length + "", 12, Ui.GREEN, true);
        secl.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 6));
        root.addView(secl);

        // category grid
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        for (int i = 0; i < Catalog.CATS.length; i++) {
            final int ci = i;
            LinearLayout c = Ui.card(this);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(MainActivity.this, CategoryActivity.class).putExtra("cat", ci));
                }
            });
            TextView e = Ui.text(this, Catalog.CATS[i][1], 22, Ui.GREEN, false);
            c.addView(e);
            c.addView(Ui.text(this, Catalog.CATS[i][0], 13, Ui.TEXT, true));
            c.addView(Ui.text(this, "25 tools", 10, Ui.DIM, false));
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.columnSpec = GridLayout.spec(i % 2, 1, 1f);
            glp.width = GridLayout.LayoutParams.WRAP_CONTENT;
            glp.topMargin = Ui.dp(this, 8);
            c.setLayoutParams(glp);
            grid.addView(c);
            Ui.reveal(c, 120 + i * 60L);
        }
        root.addView(grid);

        // bottom nav
        LinearLayout nav = Ui.row(this);
        nav.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = Ui.dp(this, 18);
        nav.setLayoutParams(nlp);
        android.widget.Button all = Ui.button(this, "☰ ALL TOOLS", Ui.GREEN);
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                page = 1;
                showAll();
            }
        });
        nav.addView(all);
        android.widget.Button about = Ui.button(this, "★ ABOUT", Ui.GOLD);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = Ui.dp(this, 10);
        about.setLayoutParams(alp);
        about.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                page = 2;
                showAbout();
            }
        });
        nav.addView(about);

        setContentView(Ui.scroll(this, root));
    }

    private void onSearch(String q) {
        resultsBox.removeAllViews();
        if (q == null || q.trim().length() < 2) return;
        String needle = q.trim().toLowerCase();
        int n = 0;
        outer:
        for (int c = 0; c < Catalog.CATS.length; c++) {
            for (int t = 0; t < 25; t++) {
                String name = Catalog.tool(c, t);
                if (name.toLowerCase().contains(needle)) {
                    final String nm = name;
                    LinearLayout item = Ui.card(this);
                    item.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Router.open(MainActivity.this, nm);
                        }
                    });
                    item.addView(Ui.text(this, name, 13, Ui.TEXT, true));
                    item.addView(Ui.text(this, Catalog.CATS[c][0], 10, Ui.DIM, false));
                    LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    ilp.topMargin = Ui.dp(this, 6);
                    item.setLayoutParams(ilp);
                    resultsBox.addView(item);
                    n++;
                    if (n >= 12) break outer;
                }
            }
        }
        if (n == 0) resultsBox.addView(Ui.text(this, "No tools found for \"" + q + "\"", 12, Ui.DIM, false));
    }

    private void showAll() {
        Intent it = new Intent(this, AllToolsActivity.class);
        startActivity(it);
        page = 0;
    }

    private void showAbout() {
        Intent it = new Intent(this, AboutActivity.class);
        startActivity(it);
        page = 0;
    }
}
