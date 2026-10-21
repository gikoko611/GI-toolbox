package com.gikoko.gitoolbox;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** All 350 tools with live search — each row with a 3D category icon. */
public class AllToolsActivity extends Activity {

    private LinearLayout list;
    private EditText search;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.header(this, "ALL TOOLS", new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));
        LinearLayout br = Ui.row(this);
        br.addView(Ui.badge(this, "● " + (Catalog.CATS.length * 25) + " CYBER TOOLS", Ui.CYAN));
        root.addView(br);
        Ui.reveal(br);
        search = Ui.input(this, "Search " + (Catalog.CATS.length * 25) + " tools...", false);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int c, int d) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                render(s.toString());
            }
        });
        root.addView(search);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        render("");
        setContentView(Ui.scroll(this, root));
    }

    private void render(String q) {
        list.removeAllViews();
        String needle = q == null ? "" : q.trim().toLowerCase();
        int shown = 0;
        for (int c = 0; c < Catalog.CATS.length; c++) {
            boolean catShown = false;
            // one shared icon per category: 14 bitmaps instead of 350 (no OOM
            // on low-RAM phones, list still gets a 3D icon on every row)
            android.graphics.drawable.Drawable catIcon = Icon3D.tile(this, Catalog.CATS[c][0], c);
            for (int t = 0; t < 25; t++) {
                final String name = Catalog.tool(c, t);
                if (needle.length() >= 1 && !name.toLowerCase().contains(needle)) continue;
                if (!catShown) {
                    TextView h = Ui.text(this, Catalog.CATS[c][0], 12, Ui.GREEN, true);
                    h.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
                    list.addView(h);
                    catShown = true;
                }
                final int cI = c;
                LinearLayout item = Ui.card(this);
                item.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Router.open(AllToolsActivity.this, name);
                    }
                });
                LinearLayout top = Ui.row(this);
                top.setGravity(Gravity.CENTER_VERTICAL);
                ImageView icon = new ImageView(this);
                icon.setImageDrawable(catIcon);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        Ui.dp(this, 40), Ui.dp(this, 40));
                icon.setLayoutParams(ilp);
                top.addView(icon);
                TextView n = Ui.text(this, name, 13, Ui.TEXT, true);
                LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                nlp.leftMargin = Ui.dp(this, 12);
                n.setLayoutParams(nlp);
                top.addView(n);
                item.addView(top);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Ui.dp(this, 6);
                item.setLayoutParams(lp);
                list.addView(item);
                shown++;
            }
        }
        TextView count = Ui.text(this, shown + " tools shown", 10, Ui.DIM, false);
        count.setPadding(0, Ui.dp(this, 10), 0, 0);
        list.addView(count);
    }
}
