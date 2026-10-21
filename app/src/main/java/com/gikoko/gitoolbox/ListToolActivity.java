package com.gikoko.gitoolbox;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Quick Notes / To-Do List / Habit Tracker / Reminder / Secure Notes / QR History. */
public class ListToolActivity extends Activity {

    private String tool;
    private SharedPreferences prefs;
    private LinearLayout list;
    private EditText input;
    private LinearLayout rootLayout;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tool = getIntent().getStringExtra("tool");
        prefs = getSharedPreferences("gitoolbox_lists", Context.MODE_PRIVATE);

        LinearLayout root = Ui.screen(this);
        rootLayout = root;
        root.addView(Ui.header(this, tool, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));

        if (tool.equals("Secure Notes")) {
            setupSecure(root);
        } else {
            setupList(root);
        }
        setContentView(Ui.scroll(this, root));
    }

    String key() {
        if (tool.equals("Quick Notes")) return "notes";
        if (tool.equals("To-Do List")) return "todo";
        if (tool.equals("Habit Tracker")) return "habits";
        if (tool.equals("Reminder")) return "reminders";
        return "qrhistory";
    }

    List<String> load() {
        List<String> items = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString(key(), "[]"));
            for (int i = 0; i < a.length(); i++) items.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return items;
    }

    void save(List<String> items) {
        JSONArray a = new JSONArray();
        for (String s : items) a.put(s);
        prefs.edit().putString(key(), a.toString()).apply();
    }

    void setupList(LinearLayout root) {
        String hint = tool.equals("Quick Notes") ? "Type a note and tap ADD"
                : tool.equals("Habit Tracker") ? "New habit, e.g. Read 10 pages"
                : tool.equals("Reminder") ? "Remind me to… e.g. Call home 7pm"
                : "New task, e.g. Fix the bug";
        input = Ui.input(this, hint, false);
        root.addView(input);

        LinearLayout row = Ui.row(this);
        Button add = Ui.button(this, "＋ ADD");
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = input.getText().toString().trim();
                if (t.length() == 0) {
                    Toast.makeText(ListToolActivity.this, "Type something first", Toast.LENGTH_SHORT).show();
                    return;
                }
                List<String> items = load();
                items.add(0, "[ ] " + t);
                save(items);
                input.setText("");
                render();
            }
        });
        Button clear = Ui.button(this, "🗑 CLEAR ALL", Ui.GOLD);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().remove(key()).apply();
                render();
                Toast.makeText(ListToolActivity.this, "Cleared", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        add.setLayoutParams(lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp2.leftMargin = Ui.dp(this, 8);
        clear.setLayoutParams(lp2);
        row.addView(add);
        row.addView(clear);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout rowPad = Ui.card(this);
        rowPad.addView(row);
        root.addView(rowPad);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        render();
    }

    void render() {
        list.removeAllViews();
        List<String> items = load();
        if (items.size() == 0) {
            TextView empty = Ui.text(this, "Nothing here yet.\nAdd your first item above ☝", 12, Ui.DIM, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(this, 24), 0, 0);
            list.addView(empty);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            String raw = items.get(i);
            boolean done = raw.startsWith("[x] ");
            final String text = done ? raw.substring(4) : raw.startsWith("[ ] ") ? raw.substring(4) : raw;

            LinearLayout item = Ui.card(this);
            LinearLayout inner = Ui.row(this);
            inner.setGravity(Gravity.CENTER_VERTICAL);

            if (tool.equals("To-Do List") || tool.equals("Habit Tracker")) {
                CheckBox cb = new CheckBox(this);
                cb.setChecked(done);
                cb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean isChecked) {
                        toggle(idx, isChecked);
                    }
                });
                inner.addView(cb);
            } else if (done) {
                TextView dot = Ui.text(this, "✔", 13, Ui.GREEN, true);
                inner.addView(dot);
            }

            TextView tv = Ui.text(this, text, 13, done ? Ui.DIM : Ui.TEXT, false);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            tv.setLayoutParams(tlp);
            inner.addView(tv);

            Button del = Ui.button(this, "✕", Ui.GOLD);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<String> items2 = load();
                    items2.remove(idx);
                    save(items2);
                    render();
                }
            });
            inner.addView(del);
            item.addView(inner);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ilp.topMargin = Ui.dp(this, 6);
            item.setLayoutParams(ilp);
            list.addView(item);
        }
    }

    void toggle(int idx, boolean done) {
        List<String> items = load();
        String raw = items.get(idx);
        String body = raw.startsWith("[ ] ") || raw.startsWith("[x] ") ? raw.substring(4) : raw;
        items.set(idx, (done ? "[x] " : "[ ] ") + body);
        save(items);
        render();
    }

    // ---------- secure notes (PIN protected) ----------
    void setupSecure(LinearLayout root) {
        final boolean hasPin = prefs.getString("secure_pin", "").length() > 0;
        final EditText pin = Ui.input(this, hasPin ? "Enter your PIN" : "Set a new PIN (digits)", false);
        pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        root.addView(pin);
        Button go = Ui.button(this, hasPin ? "🔓 UNLOCK" : "🔑 SET PIN");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String p = pin.getText().toString().trim();
                if (p.length() < 4) {
                    Toast.makeText(ListToolActivity.this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!hasPin) {
                    prefs.edit().putString("secure_pin", p).apply();
                    Toast.makeText(ListToolActivity.this, "PIN saved", Toast.LENGTH_SHORT).show();
                } else if (!p.equals(prefs.getString("secure_pin", ""))) {
                    Toast.makeText(ListToolActivity.this, "Wrong PIN", Toast.LENGTH_SHORT).show();
                    return;
                }
                pin.setVisibility(View.GONE);
                v.setVisibility(View.GONE);
                openSecureBody();
            }
        });
        root.addView(go);
    }

    void openSecureBody() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        list = content;
        input = Ui.input(this, "New secure note…", false);
        content.addView(input);
        Button add = Ui.button(this, "＋ ADD");
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = input.getText().toString().trim();
                if (t.length() == 0) return;
                List<String> items = secureLoad();
                items.add(0, t);
                secureSave(items);
                input.setText("");
                renderSecure();
            }
        });
        content.addView(add);
        rootLayout.addView(content);
        renderSecure();
    }

    List<String> secureLoad() {
        List<String> items = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString("secure_notes", "[]"));
            for (int i = 0; i < a.length(); i++) items.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return items;
    }

    void secureSave(List<String> items) {
        JSONArray a = new JSONArray();
        for (String s : items) a.put(s);
        prefs.edit().putString("secure_notes", a.toString()).apply();
    }

    void renderSecure() {
        list.removeAllViews();
        List<String> items = secureLoad();
        if (items.size() == 0) {
            list.addView(Ui.text(this, "No secure notes yet. 🔐", 12, Ui.DIM, false));
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            LinearLayout item = Ui.card(this);
            TextView tv = Ui.text(this, items.get(i), 13, Ui.TEXT, false);
            item.addView(tv);
            Button del = Ui.button(this, "✕ DELETE", Ui.GOLD);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<String> items2 = secureLoad();
                    items2.remove(idx);
                    secureSave(items2);
                    renderSecure();
                }
            });
            item.addView(del);
            list.addView(item);
        }
    }
}
