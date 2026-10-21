package com.gikoko.gitoolbox;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Slot reserved: native module under development. */
public class PanelActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final String tool = getIntent().getStringExtra("tool");
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.header(this, tool, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));
        LinearLayout c = Ui.card(this);
        TextView icon = Ui.text(this, "\u26A1", 40, Ui.GREEN, false);
        icon.setGravity(Gravity.CENTER);
        c.addView(icon);
        c.addView(Ui.text(this, tool, 16, Ui.TEXT, true));
        TextView led = Ui.text(this, "● SLOT READY · NATIVE SUITE", 11, Ui.GREEN, true);
        led.setGravity(Gravity.CENTER);
        led.setPadding(0, Ui.dp(this, 8), 0, 0);
        c.addView(led);
        TextView msg = Ui.text(this, "This tool slot is registered in the 300-tool native suite. Its engine module ships with the next APK update.", 12, Ui.TEXT, false);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
        c.addView(msg);
        root.addView(c);
        Button back = Ui.button(this, "← BACK", Ui.GREEN);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 14);
        back.setLayoutParams(lp);
        root.addView(back);
        TextView foot = Ui.text(this, "POWERED BY I KO KO", 10, Ui.GOLD, true);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Ui.dp(this, 16), 0, 0);
        root.addView(foot);
        setContentView(Ui.scroll(this, root));
    }
}
