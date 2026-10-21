package com.gikoko.gitoolbox;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Text & Code tools (25). */
final class ComputeText {

    private ComputeText() {
    }

    // field builders: fl = single line, ml = multiline
    static String[] fl(String label, String hint) {
        return new String[]{label, hint};
    }

    static String[] ml(String label, String hint) {
        return new String[]{label, hint, "multi"};
    }

    static void register() {
        Compute.reg("Word Counter", new String[][]{ml("TEXT", "Type or paste text")}, "COUNT", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String v = in.get("f0");
                if (v == null) v = "";
                String t = v.trim();
                int words = t.isEmpty() ? 0 : t.split("\\s+").length;
                return "Words:       " + words
                        + "\nCharacters:  " + v.length()
                        + "\nNo-space:    " + v.replaceAll("\\s", "").length()
                        + "\nLines:       " + v.split("\n").length
                        + "\nSentences:   " + Math.max(0, t.isEmpty() ? 0 : t.split("[.!?]+").length - 1)
                        + "\nParagraphs:  " + (t.isEmpty() ? 0 : t.split("\n\\s*\n").length);
            }
        });
        Compute.reg("Character Counter", new String[][]{ml("TEXT", "Type or paste text")}, "COUNT", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String v = nv(in.get("f0"));
                return "Characters:     " + v.length()
                        + "\nWithout spaces: " + v.replaceAll("\\s", "").length()
                        + "\nUTF-8 bytes:    " + v.getBytes().length;
            }
        });
        Compute.reg("Case Converter", new String[][]{fl("MODE", "upper / lower / title / sentence / camel / snake / kebab"),
                ml("TEXT", "Type text")}, "CONVERT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String m = Compute.s(in, "f0").toLowerCase();
                String v = nv(in.get("f1"));
                String[] words = v.trim().split("\\s+");
                StringBuilder sb = new StringBuilder();
                if (m.equals("upper")) return v.toUpperCase();
                if (m.equals("lower")) return v.toLowerCase();
                if (m.equals("title")) {
                    for (String w : words) {
                        if (w.length() > 0) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase()).append(' ');
                    }
                    return sb.toString().trim();
                }
                if (m.equals("sentence")) {
                    boolean cap = true;
                    for (char ch : v.toCharArray()) {
                        sb.append(cap ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
                        cap = ".!?".indexOf(ch) >= 0;
                    }
                    return sb.toString();
                }
                if (m.equals("camel") || m.equals("snake") || m.equals("kebab")) {
                    int i = 0;
                    for (String w : words) {
                        w = w.replaceAll("[^A-Za-z0-9]", "");
                        if (w.length() == 0) continue;
                        if (m.equals("snake")) sb.append(w.toLowerCase()).append('_');
                        else if (m.equals("kebab")) sb.append(w.toLowerCase()).append('-');
                        else sb.append(i++ == 0 ? w.toLowerCase() : Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase());
                    }
                    String r = sb.toString();
                    if ((m.equals("snake") || m.equals("kebab")) && r.endsWith("_") || r.endsWith("-") && r.length() > 0) {
                        r = r.substring(0, r.length() - 1);
                    }
                    return r;
                }
                throw new Exception("Unknown mode: " + m);
            }
        });
        Compute.reg("Reverse Text", new String[][]{ml("TEXT", "Type text")}, "REVERSE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                return new StringBuilder(nv(in.get("f0"))).reverse().toString();
            }
        });
        Compute.reg("Remove Duplicates", new String[][]{ml("LINES", "One item per line")}, "DEDUPE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String[] ls = nv(in.get("f0")).split("\n");
                Set<String> seen = new LinkedHashSet<>();
                for (String l : ls) seen.add(l.trim());
                StringBuilder sb = new StringBuilder();
                for (String l : seen) sb.append(l).append('\n');
                return sb.length() == 0 ? "(empty)" : sb.substring(0, sb.length() - 1);
            }
        });
        Compute.reg("Remove Line Breaks", new String[][]{ml("TEXT", "Type or paste text")}, "JOIN LINES", new Compute.Fn() {
            public String run(Map<String, String> in) {
                return nv(in.get("f0")).replaceAll("\\s*\\n\\s*", " ").trim();
            }
        });
        Compute.reg("Text Sorter", new String[][]{fl("ORDER", "az / za / short / long"),
                ml("LINES", "One item per line")}, "SORT", new Compute.Fn() {
            public String run(Map<String, String> in) {
                List<String> ls = new ArrayList<>(Arrays.asList(nv(in.get("f1")).split("\n")));
                while (!ls.isEmpty() && ls.get(ls.size() - 1).trim().isEmpty()) ls.remove(ls.size() - 1);
                String m = Compute.s(in, "f0").toLowerCase();
                if (m.equals("az")) Collections.sort(ls);
                else if (m.equals("za")) {
                    Collections.sort(ls);
                    Collections.reverse(ls);
                } else if (m.equals("short")) ls.sort(new java.util.Comparator<String>() {
                    public int compare(String a, String b) {
                        return a.length() - b.length();
                    }
                });
                else ls.sort(new java.util.Comparator<String>() {
                    public int compare(String a, String b) {
                        return b.length() - a.length();
                    }
                });
                return String.join("\n", ls);
            }
        });
        Compute.reg("Text Diff", new String[][]{ml("TEXT A", "First text"), ml("TEXT B", "Second text")}, "COMPARE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String[] a = nv(in.get("f0")).split("\n");
                String[] b = nv(in.get("f1")).split("\n");
                StringBuilder sb = new StringBuilder();
                int n = Math.max(a.length, b.length);
                boolean same = true;
                for (int i = 0; i < n; i++) {
                    String la = i < a.length ? a[i] : "";
                    String lb = i < b.length ? b[i] : "";
                    if (!la.trim().equals(lb.trim())) {
                        sb.append("Line ").append(i + 1).append(":\n  A: ").append(la).append("\n  B: ").append(lb).append('\n');
                        same = false;
                    }
                }
                return same ? "✔ Texts match (no differences found)" : sb.toString();
            }
        });
        Compute.reg("Lorem Ipsum", new String[][]{fl("PARAGRAPHS", "e.g. 3")}, "GENERATE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                int n = 3;
                try {
                    n = Math.max(1, Math.min(10, Integer.parseInt(Compute.s(in, "f0"))));
                } catch (Exception ignored) {
                }
                String[] words = "lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua enim ad minim veniam quis nostrud exercitation ullamco laboris nisi aliquip ex ea commodo consequat duis aute irure in reprehenderit voluptate velit esse cillum eu fugiat nulla pariatur excepteur sint occaecat cupidatat non proident sunt culpa qui officia deserunt mollit anim id est laborum".split(" ");
                java.util.Random r = new java.util.Random();
                StringBuilder sb = new StringBuilder();
                for (int p = 0; p < n; p++) {
                    int count = 40 + r.nextInt(40);
                    for (int w = 0; w < count; w++) {
                        sb.append(words[r.nextInt(words.length)]);
                        sb.append(w == count - 1 ? ". " : " ");
                    }
                    sb.append('\n');
                }
                return sb.toString();
            }
        });
        Compute.reg("Text to Slug", new String[][]{ml("TEXT", "e.g. My Blog Post Title!")}, "SLUGIFY", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String v = nv(in.get("f0")).toLowerCase().trim();
                v = v.replaceAll("[^a-z0-9\\s-]", "").replaceAll("\\s+", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
                return v.length() == 0 ? "(empty)" : v;
            }
        });
        Compute.reg("Find & Replace", new String[][]{fl("FIND", "find..."), fl("REPLACE WITH", "replacement..."),
                ml("TEXT", "Text...")}, "REPLACE ALL", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String find = Compute.s(in, "f0");
                if (find.length() == 0) return "Enter something to find";
                return nv(in.get("f2")).replace(find, Compute.s(in, "f1"));
            }
        });
        Compute.reg("Text Formatter", new String[][]{ml("TEXT", "Type or paste text")}, "CLEAN", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String[] ls = nv(in.get("f0")).split("\n");
                StringBuilder sb = new StringBuilder();
                for (String l : ls) {
                    String t = l.trim().replaceAll("\\s+", " ");
                    if (t.length() > 0) sb.append(t).append('\n');
                }
                return sb.length() == 0 ? "(empty)" : sb.substring(0, sb.length() - 1);
            }
        });
        Compute.reg("JSON Formatter", new String[][]{ml("JSON", "Paste JSON")}, "FORMAT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                Object o = new JSONTokener(nv(in.get("f0")).trim()).nextValue();
                if (o instanceof JSONObject) return ((JSONObject) o).toString(2);
                if (o instanceof JSONArray) return ((JSONArray) o).toString(2);
                throw new Exception("Not a JSON object or array");
            }
        });
        Compute.reg("JSON Validator", new String[][]{ml("JSON", "Paste JSON")}, "VALIDATE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                try {
                    new JSONTokener(nv(in.get("f0")).trim()).nextValue();
                    return "✔ VALID JSON";
                } catch (Exception e) {
                    return "✘ INVALID JSON\n" + e.getMessage();
                }
            }
        });
        Compute.reg("JSON → XML", new String[][]{ml("JSON", "Paste JSON object")}, "CONVERT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                JSONObject o = new JSONObject(nv(in.get("f0")).trim());
                return jsonToXml(o, "root");
            }
        });
        Compute.reg("XML → JSON", new String[][]{ml("XML", "Paste simple XML")}, "CONVERT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                return xmlToJson(nv(in.get("f0")).trim()).toString(2);
            }
        });
        Compute.reg("CSV → JSON", new String[][]{ml("CSV", "name,age\nAlice,30")}, "CONVERT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String[] ls = nv(in.get("f0")).split("\n");
                if (ls.length < 2) throw new Exception("Need a header row + data row");
                String[] head = ls[0].split(",");
                JSONArray a = new JSONArray();
                for (int i = 1; i < ls.length; i++) {
                    if (ls[i].trim().isEmpty()) continue;
                    String[] vals = ls[i].split(",");
                    JSONObject o = new JSONObject();
                    for (int j = 0; j < head.length; j++) {
                        o.put(head[j].trim(), j < vals.length ? vals[j].trim() : "");
                    }
                    a.put(o);
                }
                return a.toString(2);
            }
        });
        Compute.reg("JSON → CSV", new String[][]{ml("JSON ARRAY", "[{\"name\":\"x\",\"age\":1}]")}, "CONVERT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                JSONArray a = new JSONArray(nv(in.get("f0")).trim());
                if (a.length() == 0) return "(empty)";
                JSONObject first = a.getJSONObject(0);
                JSONArray keys = first.names();
                if (keys == null) return "(empty)";
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < keys.length(); j++) sb.append(keys.getString(j)).append(j == keys.length() - 1 ? "" : ",");
                sb.append('\n');
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    for (int j = 0; j < keys.length(); j++) {
                        sb.append(o.optString(keys.getString(j), "")).append(j == keys.length() - 1 ? "" : ",");
                    }
                    sb.append('\n');
                }
                return sb.toString();
            }
        });
        Compute.reg("SQL Formatter", new String[][]{ml("SQL", "select * from users where id=1")}, "FORMAT", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String v = nv(in.get("f0"));
                v = v.replaceAll("(?i)\\b(select|from|where|group by|order by|having|limit|insert into|values|update|set|delete|join|left join|right join|inner join)\\b", "\n$1");
                v = v.replaceAll("\\s*\\n\\s*", "\n").trim();
                return v;
            }
        });
        Compute.reg("HTML Formatter", new String[][]{ml("HTML", "Paste HTML")}, "FORMAT", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String v = nv(in.get("f0"));
                v = v.replaceAll(">", ">\n").replaceAll("\\s*\\n\\s*", "\n");
                return v.trim();
            }
        });
        Compute.reg("CSS Formatter", new String[][]{ml("CSS", "Paste CSS")}, "FORMAT", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String v = nv(in.get("f0"));
                v = v.replaceAll("\\s*\\{\\s*", " {\n  ").replaceAll("\\s*;\\s*", ";\n  ").replaceAll("\\s*\\}\\s*", "\n}\n\n");
                return v.replaceAll("\\n  \\n", "\n").trim();
            }
        });
        Compute.reg("JS Minifier", new String[][]{ml("JAVASCRIPT", "Paste JS code")}, "MINIFY", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String v = nv(in.get("f0"));
                v = v.replaceAll("//[^\n]*", "").replaceAll("(?s)/\\*.*?\\*/", "");
                v = v.replaceAll("\\s+", " ").trim();
                return v;
            }
        });
        Compute.reg("CSS Minifier", new String[][]{ml("CSS", "Paste CSS")}, "MINIFY", new Compute.Fn() {
            public String run(Map<String, String> in) {
                return nv(in.get("f0")).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("\\s+", " ").replaceAll("\\s*([{};:,])\\s*", "$1").trim();
            }
        });
        Compute.reg("Markdown Preview", new String[][]{ml("MARKDOWN", "# Title\n\nSome **bold** text\n\n- item 1\n- item 2")}, "PREVIEW", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String[] ls = nv(in.get("f0")).split("\n");
                StringBuilder sb = new StringBuilder();
                for (String l : ls) {
                    if (l.startsWith("### ")) sb.append("   ").append(l.substring(4)).append('\n');
                    else if (l.startsWith("## ")) sb.append("  ").append(l.substring(3)).append('\n');
                    else if (l.startsWith("# ")) sb.append(l.substring(2)).append('\n');
                    else if (l.matches("^[-*] .*")) sb.append("  • ").append(l.substring(2)).append('\n');
                    else if (l.matches("^\\d+\\. .*")) sb.append("  ").append(l).append('\n');
                    else sb.append(l).append('\n');
                }
                return sb.toString();
            }
        });
        Compute.reg("Text Encrypter", new String[][]{fl("KEY", "password"),
                ml("TEXT", "Text to encrypt or decrypt")}, "🔒 ENCRYPT / DECRYPT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String key = Compute.s(in, "f0");
                if (key.length() == 0) throw new Exception("Enter a key");
                String text = nv(in.get("f1"));
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < text.length(); i++) {
                    sb.append((char) (text.charAt(i) ^ key.charAt(i % key.length())));
                }
                return sb.toString();
            }
        });
    }

    static String nv(String v) {
        return v == null ? "" : v;
    }

    private static String jsonToXml(JSONObject o, String tag) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append('<').append(tag).append('>');
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object v = o.get(k);
            if (v instanceof JSONObject) sb.append(jsonToXml((JSONObject) v, k));
            else if (v instanceof JSONArray) {
                JSONArray a = (JSONArray) v;
                for (int i = 0; i < a.length(); i++) {
                    Object av = a.get(i);
                    if (av instanceof JSONObject) sb.append(jsonToXml((JSONObject) av, k));
                    else sb.append('<').append(k).append('>').append(av).append("</").append(k).append('>');
                }
            } else sb.append('<').append(k).append('>').append(v).append("</").append(k).append('>');
        }
        sb.append("</").append(tag).append('>');
        return sb.toString();
    }

    private static JSONObject xmlToJson(String xml) throws Exception {
        JSONObject o = new JSONObject();
        if (xml.startsWith("<?")) xml = xml.substring(xml.indexOf("?>") + 2);
        xml = xml.trim();
        if (!xml.startsWith("<")) throw new Exception("Not XML");
        int sp = xml.indexOf('>');
        String open = xml.substring(1, sp).split("\\s")[0];
        int close = xml.lastIndexOf("</" + open + ">");
        if (close < 0) throw new Exception("Malformed XML");
        String inner = xml.substring(sp + 1, close).trim();
        parseChildren(inner, o);
        return o;
    }

    private static void parseChildren(String xml, JSONObject o) {
        int i = 0;
        while (i < xml.length() && xml.charAt(i) == '<') {
            int sp = xml.indexOf('>', i);
            if (sp < 0) return;
            String open = xml.substring(i + 1, sp).split("\\s")[0];
            int close = xml.indexOf("</" + open + ">", sp);
            if (close < 0) return;
            String inner = xml.substring(sp + 1, close).trim();
            if (inner.startsWith("<")) {
                try {
                    JSONObject child = new JSONObject();
                    parseChildren(inner, child);
                    o.accumulate(open, child);
                } catch (Exception e) {
                    try { o.accumulate(open, inner); } catch (Exception ignored) { }
                }
            } else {
                try { o.accumulate(open, inner); } catch (Exception ignored) { }
            }
            i = close + open.length() + 3;
        }
    }
}
