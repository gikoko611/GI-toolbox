package com.gikoko.gitoolbox;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

/** Generic form tool screen powered by the Compute registry. */
public class ToolActivity extends Activity {

    private Compute.Spec spec;
    private EditText[] inputs;
    private TextView output;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final String tool = getIntent().getStringExtra("tool");
        spec = Compute.get(tool);
        if (spec == null) {
            Router.open(this, tool);
            finish();
            return;
        }
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.header(this, tool, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));

        LinearLayout card = Ui.card(this);
        inputs = new EditText[spec.fields.length];
        for (int i = 0; i < spec.fields.length; i++) {
            String label = spec.fields[i][0];
            String hint = spec.fields[i][1];
            boolean multi = spec.fields[i].length > 2 && spec.fields[i][2].equals("multi");
            if (label.length() > 0) {
                TextView lb = Ui.text(this, label.toUpperCase(), 11, Ui.GREEN, true);
                lb.setPadding(0, Ui.dp(this, i == 0 ? 0 : 12), 0, Ui.dp(this, 6));
                card.addView(lb);
            }
            EditText e = Ui.input(this, hint, multi);
            card.addView(e);
            inputs[i] = e;
        }

        Button run = Ui.button(this, spec.button == null ? "⚡ RUN" : spec.button, Ui.GREEN);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(this, 14);
        run.setLayoutParams(rlp);
        card.addView(run);
        root.addView(card);

        output = new TextView(this);
        output.setTextColor(Ui.TEXT);
        output.setTextSize(13);
        Ui.applyTypeface(output, false, this);
        output.setTextIsSelectable(true);

        LinearLayout outCard = Ui.card(this);
        outCard.addView(Ui.text(this, "RESULT", 11, Ui.DIM, true));
        outCard.addView(output);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        olp.topMargin = Ui.dp(this, 10);
        outCard.setLayoutParams(olp);
        root.addView(outCard);

        final Button copy = Ui.button(this, "📋 COPY RESULT", Ui.GOLD);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(this, 10);
        copy.setLayoutParams(clp);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("GIToolbox", output.getText().toString()));
                    Toast.makeText(ToolActivity.this, "Copied", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(ToolActivity.this, "Copy failed", Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(copy);

        run.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Map<String, String> vals = new HashMap<>();
                for (int i = 0; i < spec.fields.length; i++) {
                    vals.put("f" + i, inputs[i].getText().toString());
                }
                try {
                    String out = spec.fn.run(vals);
                    output.setText(out == null || out.length() == 0 ? "(empty)" : out);
                } catch (Exception e) {
                    output.setText("Error: " + e.getMessage());
                }
            }
        });

        TextView foot = Ui.text(this, "POWERED BY I KO KO", 10, Ui.GOLD, true);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Ui.dp(this, 16), 0, 0);
        root.addView(foot);

        setContentView(Ui.scroll(this, root));
    }
}
