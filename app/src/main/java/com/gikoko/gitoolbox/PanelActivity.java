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
        Ui.breathe(icon);
        c.addView(Ui.text(this, tool, 16, Ui.TEXT, true));
        TextView led = Ui.badge(this, "● SLOT RESERVED", Ui.GOLD);
        led.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dp(this, 8);
        led.setLayoutParams(llp);
        c.addView(led);
        TextView msg = Ui.text(this, "This tool slot is registered in the 300-tool native suite.\n\nA few engines need deep platform modules (camera scanning, DRM-signed media, low-level codecs) that cannot run reliably on every device yet — so their slot stays reserved rather than showing a broken screen.\n\nThey ship in the next APK update.", 12, Ui.TEXT, false);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
        c.addView(msg);
        root.addView(c);
        Ui.reveal(c);
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
