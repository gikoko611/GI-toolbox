package com.gikoko.gitoolbox;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Lists the 25 tools of a category. */
public class CategoryActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final int ci = getIntent().getIntExtra("cat", 0);
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.header(this, Catalog.CATS[ci][0] + " " + Catalog.CATS[ci][1], new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));
        for (int t = 0; t < 25; t++) {
            final String name = Catalog.tool(ci, t);
            LinearLayout item = Ui.card(this);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Router.open(CategoryActivity.this, name);
                }
            });
            item.addView(Ui.text(this, name, 14, Ui.TEXT, true));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 8);
            item.setLayoutParams(lp);
            root.addView(item);
        }
        TextView foot = Ui.text(this, "POWERED BY I KO KO", 11, Ui.GOLD, true);
        foot.setPadding(0, Ui.dp(this, 16), 0, 0);
        root.addView(foot);
        setContentView(Ui.scroll(this, root));
    }
}
