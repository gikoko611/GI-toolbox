package com.gikoko.gitoolbox;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.header(this, "ABOUT", new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));
        LinearLayout c = Ui.card(this);
        TextView gi = new TextView(this);
        gi.setText("GI");
        gi.setTextSize(44);
        gi.setTextColor(Ui.GOLD);
        gi.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
        gi.setGravity(android.view.Gravity.CENTER);
        c.addView(gi);
        c.addView(Ui.gold(this, "GIToolbox NATIVE", 22));
        c.addView(Ui.text(this, "ALL-IN-ONE · " + (Catalog.CATS.length * 25) + " FEATURES · v0.0.2 CYBER", 12, Ui.DIM, false));
        root.addView(c);

        LinearLayout c2 = Ui.card(this);
        c2.addView(Ui.text(this, "100% NATIVE ANDROID ENGINE", 13, Ui.GREEN, true));
        c2.addView(Ui.text(this, "No WebView. Every screen is built with native Android views and Java code — splash, home, search, categories and tools all run on your phone's own engine.", 12, Ui.TEXT, false));
        c2.addView(Ui.text(this, "Cyber 3D Edition — Neon • Cyan • Magenta", 12, Ui.TEXT, false));
        c2.addView(Ui.text(this, "Most tools run fully offline.", 12, Ui.TEXT, false));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = Ui.dp(this, 10);
        c2.setLayoutParams(lp2);
        root.addView(c2);

        LinearLayout c3 = Ui.card(this);
        TextView brand = Ui.gold(this, "POWERED BY I KO KO", 16);
        brand.setGravity(android.view.Gravity.CENTER);
        c3.addView(brand);
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp3.topMargin = Ui.dp(this, 10);
        c3.setLayoutParams(lp3);
        root.addView(c3);

        Button back = Ui.button(this, "← BACK HOME", Ui.GREEN);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 16);
        back.setLayoutParams(blp);
        root.addView(back);

        setContentView(Ui.scroll(this, root));
    }
}
