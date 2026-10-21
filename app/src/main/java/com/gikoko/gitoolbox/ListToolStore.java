package com.gikoko.gitoolbox;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Small storage helper for the QR History list tool. */
final class ListToolStore {

    private ListToolStore() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("gitoolbox_lists", Context.MODE_PRIVATE);
    }

    static List<String> load(Context c) {
        List<String> items = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("qrhistory", "[]"));
            for (int i = 0; i < a.length(); i++) items.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return items;
    }

    static void add(Context c, String entry) {
        List<String> items = load(c);
        items.add(0, "[ ] " + entry);
        while (items.size() > 50) items.remove(items.size() - 1);
        JSONArray a = new JSONArray();
        for (String s : items) a.put(s);
        prefs(c).edit().putString("qrhistory", a.toString()).apply();
    }
}
