package com.gikoko.gitoolbox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** AI Tools (24 offline implementations; Text Translator ships as a guided panel). */
final class ComputeAi {

    private ComputeAi() {
    }

    static final Random RNG = new Random();

    static String[] fl(String l, String h) {
        return new String[]{l, h};
    }

    static String[] ml(String l, String h) {
        return new String[]{l, h, "multi"};
    }

    // word frequency summary
    static String summarize(String text, int maxSentences) {
        String t = text.replace("\n", " ").trim();
        if (t.length() == 0) return "(empty)";
        String[] sents = t.split("(?<=[.!?])\\s+");
        String[] words = t.toLowerCase(Locale.US).split("[^a-z0-9']+");
        HashMap<String, Integer> freq = new HashMap<>();
        for (String w : words) {
            if (w.length() < 4 || STOP.contains(w)) continue;
            freq.put(w, freq.containsKey(w) ? freq.get(w) + 1 : 1);
        }
        // rank sentences by sum of word freq
        double[] score = new double[sents.length];
        for (int i = 0; i < sents.length; i++) {
            for (String w : sents[i].toLowerCase(Locale.US).split("[^a-z0-9']+")) {
                Integer f = freq.get(w);
                if (f != null) score[i] += f;
            }
            score[i] /= Math.sqrt(sents[i].length() + 1);
        }
        List<Integer> top = new ArrayList<>();
        boolean[] used = new boolean[sents.length];
        for (int k = 0; k < Math.min(maxSentences, sents.length); k++) {
            int best = -1;
            for (int i = 0; i < score.length; i++) {
                if (!used[i] && (best < 0 || score[i] > score[best])) best = i;
            }
            if (best < 0) break;
            used[best] = true;
            top.add(best);
        }
        java.util.Collections.sort(top);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(sents[top.get(i)].trim());
        }
        // top keywords
        List<String> kw = new ArrayList<>(freq.keySet());
        java.util.Collections.sort(kw, new java.util.Comparator<String>() {
            public int compare(String a, String b) {
                return freq.get(b) - freq.get(a);
            }
        });
        StringBuilder ksb = new StringBuilder();
        for (int i = 0; i < Math.min(8, kw.size()); i++) {
            if (i > 0) ksb.append(", ");
            ksb.append(kw.get(i));
        }
        return "SUMMARY (" + top.size() + "/" + sents.length + " sentences):\n\n" + sb
                + "\n\nKEYWORDS: " + ksb;
    }

    static final Set<String> STOP = new LinkedHashSet<>();
    static final HashMap<String, String> SYN = new HashMap<>();
    static final HashMap<String, String> EMOJI = new HashMap<>();

    static {
        String[] sw = {"this", "that", "with", "from", "have", "they", "been", "were", "will", "your",
                "what", "when", "then", "them", "there", "here", "would", "could", "should", "which",
                "into", "about", "after", "over", "also", "just", "like", "some", "more", "very", "than",
                "the", "and", "for", "are", "but", "not", "you", "all", "can", "her", "was", "one", "our",
                "out", "his", "she", "him", "has", "had", "how", "who", "why", "any", "its", "it's"};
        for (String s : sw) STOP.add(s);
        String[][] syn = {{"good", "excellent"}, {"bad", "poor"}, {"big", "large"}, {"small", "compact"},
                {"fast", "rapid"}, {"slow", "unhurried"}, {"happy", "delighted"}, {"sad", "downcast"},
                {"smart", "clever"}, {"easy", "simple"}, {"hard", "difficult"}, {"important", "crucial"},
                {"make", "create"}, {"use", "utilize"}, {"get", "obtain"}, {"show", "display"},
                {"help", "assist"}, {"start", "begin"}, {"end", "conclude"}, {"think", "consider"},
                {"look", "appear"}, {"need", "require"}, {"build", "construct"}, {"buy", "purchase"},
                {"new", "modern"}, {"old", "traditional"}, {"great", "outstanding"}, {"see", "view"}};
        for (String[] s : syn) SYN.put(s[0], s[1]);
        String[][] em = {{"happy", "😄"}, {"sad", "😢"}, {"love", "❤️"}, {"fire", "🔥"}, {"cool", "😎"},
                {"star", "⭐"}, {"code", "💻"}, {"work", "💼"}, {"money", "💰"}, {"food", "🍕"},
                {"time", "⏰"}, {"music", "🎵"}, {"game", "🎮"}, {"phone", "📱"}, {"photo", "📷"},
                {"night", "🌙"}, {"sun", "☀️"}, {"rain", "🌧️"}, {"car", "🚗"}, {"home", "🏠"},
                {"book", "📚"}, {"coffee", "☕"}, {"green", "💚"}, {"gold", "🥇"}, {"power", "⚡"},
                {"idea", "💡"}, {"rocket", "🚀"}, {"party", "🎉"}, {"sleep", "😴"}, {"run", "🏃"}};
        for (String[] e : em) EMOJI.put(e[0], e[1]);
    }

    static void register() {

        Compute.reg("Text Summarizer", new String[][]{ml("TEXT", "Paste a long text"),
                fl("SENTENCES", "max sentences (blank = 3)")}, "SUMMARIZE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                int n = Compute.s(in, "f1").length() == 0 ? 3 : Compute.i(in, "f1");
                if (n < 1 || n > 20) throw new Exception("Sentences must be 1-20");
                return summarize(ComputeText.nv(in.get("f0")), n);
            }
        });

        Compute.reg("Grammar Fixer", new String[][]{ml("TEXT", "Text with typos")}, "FIX", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String v = ComputeText.nv(in.get("f0")).trim();
                if (v.length() == 0) return "(empty)";
                // capitalize sentence starts, fix spacing, double spaces, i -> I, common fixes
                v = v.replaceAll("\\s+", " ");
                StringBuilder sb = new StringBuilder(v);
                boolean cap = true;
                for (int i = 0; i < sb.length(); i++) {
                    char c = sb.charAt(i);
                    if (cap && Character.isLetter(c)) {
                        sb.setCharAt(i, Character.toUpperCase(c));
                        cap = false;
                    } else if (".!?".indexOf(c) >= 0) {
                        cap = true;
                    }
                }
                String r = sb.toString();
                r = r.replaceAll("\\bi\\b", "I");
                r = r.replaceAll("\\s+([,.!?;:])", "$1");
                r = r.replaceAll("([,.!?;:])([A-Za-z])", "$1 $2");
                r = r.replaceAll("(?i)\\bteh\\b", "the");
                r = r.replaceAll("(?i)\\brecieve\\b", "receive");
                r = r.replaceAll("(?i)\\bdefinately\\b", "definitely");
                r = r.replaceAll("(?i)\\balot\\b", "a lot");
                r = r.replaceAll("(?i)\\bdont\\b", "don't");
                r = r.replaceAll("(?i)\\bcant\\b", "can't");
                r = r.replaceAll("(?i)\\bdoesnt\\b", "doesn't");
                r = r.replaceAll("(?i)\\bwont\\b", "won't");
                r = r.replaceAll("(?i)\\bim\\b", "I'm");
                r = r.replaceAll("(?i)\\bpls\\b", "please");
                r = r.replaceAll("(?i)\\bu\\b", "you");
                return "FIXED:\n\n" + r;
            }
        });

        Compute.reg("Paraphraser", new String[][]{ml("TEXT", "Text to rephrase")}, "REPHRASE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String v = ComputeText.nv(in.get("f0")).trim();
                String[] words = v.split("\\s+");
                StringBuilder sb = new StringBuilder();
                for (String w : words) {
                    String low = w.toLowerCase(Locale.US).replaceAll("[^a-z']", "");
                    String rep = SYN.containsKey(low) ? SYN.get(low) : null;
                    if (rep != null) {
                        if (Character.isUpperCase(w.charAt(0))) rep = Character.toUpperCase(rep.charAt(0)) + rep.substring(1);
                        sb.append(rep);
                    } else sb.append(w);
                    sb.append(' ');
                }
                String r = sb.toString().trim();
                if (r.equals(v)) r = r + "\n\n(No synonyms found — text is already unique.)";
                return r;
            }
        });

        Compute.reg("AI Writer", new String[][]{fl("TOPIC", "What to write about"),
                fl("TYPE", "intro / body / conclusion (blank = intro)")}, "WRITE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String topic = Compute.s(in, "f0");
                if (topic.length() == 0) throw new IllegalArgumentException("Enter a topic");
                String type = Compute.s(in, "f1").toLowerCase(Locale.US);
                if (type.length() == 0) type = "intro";
                if (type.startsWith("i")) {
                    return topic + " has become an essential subject in today's fast-moving world. "
                            + "Understanding its core principles helps anyone make better decisions, save time, and unlock new opportunities. "
                            + "In this short piece, we explore what makes " + topic + " important, how it works in practice, and the key points to remember.\n\n"
                            + "At its foundation, " + topic + " combines simple ideas with consistent execution. "
                            + "Anyone can start today: define your goal, break it into small steps, and track progress. "
                            + "The most successful people treat " + topic + " as a habit, not a one-time task.\n\n"
                            + "In conclusion, mastering " + topic + " is within reach for everyone willing to learn. Start small, stay consistent, and results will follow.";
                }
                if (type.startsWith("b")) {
                    return "There are three pillars behind effective " + topic + ".\n\n"
                            + "1. Clarity — know exactly what result you want from " + topic + ".\n"
                            + "2. Consistency — small daily actions beat rare big efforts.\n"
                            + "3. Feedback — review results weekly and adjust your approach.\n\n"
                            + "Apply these pillars and " + topic + " becomes predictable instead of frustrating.";
                }
                return "To sum up: " + topic + " rewards those who start now and stay consistent. "
                        + "Pick one action you can take in the next 10 minutes, do it, and build from there. "
                        + "The best time to begin with " + topic + " was yesterday — the second best time is today.";
            }
        });

        Compute.reg("Code Explainer", new String[][]{fl("LANGUAGE", "e.g. java / js / python (optional)"),
                ml("CODE", "Paste code")}, "EXPLAIN", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String code = ComputeText.nv(in.get("f1")).trim();
                if (code.length() == 0) return "(empty)";
                StringBuilder sb = new StringBuilder("STRUCTURE ANALYSIS:\n\n");
                String[] lines = code.split("\n");
                int classes = count(code, "class "), funcs = 0, ifs = count(code, "if "), loops = 0;
                loops += count(code, "for ") + count(code, "while ");
                funcs += count(code, "def ") + count(code, "function ") + countRegex(code, "\\w+\\s+\\w+\\([^)]*\\)\\s*\\{");
                sb.append("Lines:          ").append(lines.length).append('\n');
                sb.append("Classes:        ").append(classes).append('\n');
                sb.append("Functions:      ~").append(funcs).append('\n');
                sb.append("If branches:    ").append(ifs).append('\n');
                sb.append("Loops:          ").append(loops).append('\n');
                sb.append("Comments:       ").append(count(code, "//") + count(code, "#")).append('\n');
                sb.append('\n').append("The code defines ").append(classes > 0 ? classes + " class(es) " : "logic ")
                        .append("with about ").append(funcs).append(" function(s), ")
                        .append(ifs).append(" conditional branch(es) and ").append(loops).append(" loop(s). ")
                        .append(loops == 0 && ifs == 0 ? "It runs top-to-bottom without branching — simple linear logic."
                                : "Control flow: inputs are validated, then processed inside the branch/loop structure.");
                return sb.toString();
            }
        });

        Compute.reg("Code Generator", new String[][]{fl("WHAT", "e.g. login form, fibonacci, http request")}, "GENERATE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String w = Compute.s(in, "f0").toLowerCase(Locale.US);
                if (w.contains("fibonacci")) {
                    return "// Fibonacci in Java\nint a = 0, b = 1;\nfor (int i = 0; i < 10; i++) {\n    System.out.println(a);\n    int next = a + b;\n    a = b;\n    b = next;\n}";
                }
                if (w.contains("login") || w.contains("form")) {
                    return "<!-- Simple login form -->\n<form onsubmit=\"return check(this)\">\n  <input name=\"user\" placeholder=\"Username\" required>\n  <input name=\"pass\" type=\"password\" placeholder=\"Password\" required>\n  <button type=\"submit\">Login</button>\n</form>\n<script>\nfunction check(f) {\n  if (f.user.value && f.pass.value.length >= 6) return true;\n  alert('Invalid credentials');\n  return false;\n}\n</script>";
                }
                if (w.contains("http") || w.contains("request") || w.contains("api")) {
                    return "// Java HTTP GET request\nURL url = new URL(\"https://api.example.com/data\");\nHttpURLConnection c = (HttpURLConnection) url.openConnection();\nc.setRequestMethod(\"GET\");\nc.setConnectTimeout(10000);\nBufferedReader r = new BufferedReader(\n    new InputStreamReader(c.getInputStream()));\nStringBuilder sb = new StringBuilder();\nString line;\nwhile ((line = r.readLine()) != null) sb.append(line);\nr.close();\nSystem.out.println(sb);";
                }
                if (w.contains("sort")) {
                    return "// Bubble sort (Java)\nvoid sort(int[] a) {\n    for (int i = 0; i < a.length - 1; i++)\n        for (int j = 0; j < a.length - 1 - i; j++)\n            if (a[j] > a[j + 1]) {\n                int t = a[j]; a[j] = a[j + 1]; a[j + 1] = t;\n            }\n}";
                }
                return "// Skeleton for: " + Compute.s(in, "f0") + "\npublic class Task {\n    public static void main(String[] args) {\n        // 1. Read input\n        // 2. Validate\n        // 3. Process\n        // 4. Output result\n        System.out.println(\"TODO: implement " + Compute.s(in, "f0") + "\");\n    }\n}";
            }
        });

        Compute.reg("Regex Explainer", new String[][]{fl("REGEX", "e.g. ^[a-z0-9._%+-]+@\\S+\\.\\S+$")}, "EXPLAIN", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String r = Compute.s(in, "f0");
                StringBuilder sb = new StringBuilder();
                for (char c : r.toCharArray()) {
                    String m = "^" + c + " matches literal '" + c + "'";
                    switch (c) {
                        case '^': m = "^  start of string"; break;
                        case '$': m = "$  end of string"; break;
                        case '.': m = ".  any character"; break;
                        case '*': m = "*  0 or more of previous"; break;
                        case '+': m = "+  1 or more of previous"; break;
                        case '?': m = "?  optional (0 or 1)"; break;
                        case '[': m = "[  character class start"; break;
                        case ']': m = "]  character class end"; break;
                        case '{': m = "{  quantifier start {n,m}"; break;
                        case '}': m = "}  quantifier end"; break;
                        case '|': m = "|  OR alternative"; break;
                        case '(': m = "(  group start"; break;
                        case ')': m = ")  group end"; break;
                        case '\\': m = "\\\\ escape / shorthand (\\d \\w \\s ...)"; break;
                        default:
                            if (Character.isLetterOrDigit(c)) m = "'" + c + "'  literal character";
                            else m = "'" + c + "'  literal";
                    }
                    sb.append(m).append('\n');
                }
                return "REGEX: " + r + "\n\nTOKENS:\n" + sb.toString().trim()
                        + "\n\nTest it in the Regex Builder tool.";
            }
        });

        Compute.reg("Regex Builder", new String[][]{fl("PATTERN FOR", "email / url / phone / ip / number / date")}, "BUILD", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String w = Compute.s(in, "f0").toLowerCase(Locale.US);
                if (w.contains("mail")) return "Email:\n^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
                if (w.contains("url") || w.contains("site")) return "URL:\nhttps?://[\\w.-]+\\.[a-z]{2,}(/[\\w./?%&=-]*)?";
                if (w.contains("phone") || w.contains("tel")) return "Phone (+95 etc):\n\\+?\\d{1,3}[ -]?\\d{3,4}[ -]?\\d{4,5}";
                if (w.equals("ip") || w.contains("ip ")) return "IPv4:\n^(\\d{1,3}\\.){3}\\d{1,3}$";
                if (w.contains("num")) return "Number (int or decimal):\n^-?\\d+(\\.\\d+)?$";
                if (w.contains("date")) return "Date yyyy-MM-dd:\n^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$";
                throw new Exception("Try: email / url / phone / ip / number / date");
            }
        });

        Compute.reg("Text to Emoji", new String[][]{ml("TEXT", "Type text")}, "EMOJIFY", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String v = ComputeText.nv(in.get("f0"));
                StringBuilder sb = new StringBuilder();
                for (String w : v.split("\\s+")) {
                    String low = w.toLowerCase(Locale.US).replaceAll("[^a-z']", "");
                    sb.append(w);
                    String e = EMOJI.get(low);
                    if (e != null) sb.append(' ').append(e);
                    sb.append(' ');
                }
                return sb.toString().trim();
            }
        });

        Compute.reg("Tone Analyzer", new String[][]{ml("TEXT", "Message to analyze")}, "ANALYZE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String v = ComputeText.nv(in.get("f0")).toLowerCase(Locale.US);
                int pos = 0, neg = 0;
                String[] pw = {"good", "great", "love", "happy", "excellent", "awesome", "thanks", "best", "nice", "amazing", "win"};
                String[] nw = {"bad", "hate", "angry", "worst", "terrible", "sad", "poor", "fail", "never", "problem", "stupid"};
                for (String w : pw) if (v.contains(w)) pos++;
                for (String w : nw) if (v.contains(w)) neg++;
                String tone = pos > neg ? "POSITIVE 😊" : neg > pos ? "NEGATIVE 😠" : v.contains("?") ? "CURIOUS ❓" : "NEUTRAL 😐";
                return "Tone: " + tone + "\nPositive signals: " + pos + "\nNegative signals: " + neg
                        + "\n\nTip: " + (tone.startsWith("NEGATIVE") ? "Consider softening the wording." : "Wording looks fine.");
            }
        });

        Compute.reg("Keyword Extractor", new String[][]{ml("TEXT", "Paste text")}, "EXTRACT", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String t = ComputeText.nv(in.get("f0")).toLowerCase(Locale.US);
                String[] words = t.split("[^a-z0-9']+");
                HashMap<String, Integer> freq = new HashMap<>();
                for (String w : words) {
                    if (w.length() < 4 || STOP.contains(w)) continue;
                    freq.put(w, freq.containsKey(w) ? freq.get(w) + 1 : 1);
                }
                List<String> kw = new ArrayList<>(freq.keySet());
                java.util.Collections.sort(kw, new java.util.Comparator<String>() {
                    public int compare(String a, String b) {
                        return freq.get(b) - freq.get(a);
                    }
                });
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(15, kw.size()); i++) {
                    sb.append(i + 1).append(". ").append(kw.get(i)).append("  (").append(freq.get(kw.get(i))).append("x)\n");
                }
                return sb.length() == 0 ? "(no keywords found)" : sb.toString().trim();
            }
        });

        Compute.reg("Name Generator", new String[][]{fl("TYPE", "person / brand / gamertag / band (blank = person)"),
                fl("COUNT", "how many (blank = 8)")}, "GENERATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String type = Compute.s(in, "f0").toLowerCase(Locale.US);
                if (type.length() == 0) type = "person";
                int c = Compute.s(in, "f1").length() == 0 ? 8 : Compute.i(in, "f1");
                if (c < 1 || c > 30) throw new Exception("Count 1-30");
                String[] first = {"Ava", "Luna", "Kai", "Nova", "Zane", "Mira", "Axel", "Ruby", "Jade", "Rex", "Iris", "Milo"};
                String[] last = {"Stone", "Vale", "Cross", "Storm", "Wolf", "Blake", "Ray", "Fox", "Hart", "Knox"};
                String[] brand1 = {"Neo", "Zen", "Quantum", "Pixel", "Hyper", "Echo", "Aero", "Prime", "Nova", "Ultra"};
                String[] brand2 = {"Lab", "Works", "Forge", "Flow", "Core", "Sync", "Byte", "Wave", "Grid", "Hub"};
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < c; i++) {
                    if (i > 0) sb.append('\n');
                    if (type.startsWith("b")) sb.append(brand1[RNG.nextInt(brand1.length)]).append(brand2[RNG.nextInt(brand2.length)]);
                    else if (type.startsWith("g")) sb.append(brand1[RNG.nextInt(brand1.length)]).append('_').append(RNG.nextInt(999)).append(RNG.nextBoolean() ? "GG" : "X");
                    else if (type.startsWith("band") || type.startsWith("m")) sb.append("The ").append(brand1[RNG.nextInt(brand1.length)]).append(' ').append(brand2[RNG.nextInt(brand2.length)]);
                    else sb.append(first[RNG.nextInt(first.length)]).append(' ').append(last[RNG.nextInt(last.length)]);
                }
                return sb.toString();
            }
        });

        Compute.reg("Bio Generator", new String[][]{fl("NAME / INTEREST", "e.g. Ko | coding, gaming")}, "GENERATE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String n = Compute.s(in, "f0");
                if (n.length() == 0) n = "Your Name";
                return "OPTION 1:\n" + n + " | Building things that matter ⚡ | Coffee first ☕\n\n"
                        + "OPTION 2:\n✨ " + n + "\n- Dreamer, doer, learner\n- 100% original content\n- DM for collabs 📩\n\n"
                        + "OPTION 3:\n" + n + " — turning ideas into reality 🚀\nPowered by curiosity. Powered by I KO KO.";
            }
        });

        Compute.reg("Email Writer", new String[][]{fl("PURPOSE", "e.g. leave request, follow-up, apology"),
                fl("RECIPIENT", "e.g. my manager")}, "WRITE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String p = Compute.s(in, "f0").toLowerCase(Locale.US);
                String r = Compute.s(in, "f1").length() == 0 ? "Sir/Madam" : Compute.s(in, "f1");
                if (p.contains("leave")) {
                    return "Subject: Leave Request\n\nDear " + r + ",\n\nI would like to request leave from [start date] to [end date] due to [reason]. "
                            + "I will ensure my tasks are up to date before leaving and will hand over urgent items to a colleague.\n\n"
                            + "Please let me know if you need any further information.\n\nSincerely,\n[Your name]";
                }
                if (p.contains("follow") || p.contains("remind")) {
                    return "Subject: Following up\n\nDear " + r + ",\n\nI hope you are doing well. I am following up on my previous message regarding [topic]. "
                            + "I would appreciate your feedback when you have a moment.\n\nThank you for your time.\n\nBest regards,\n[Your name]";
                }
                if (p.contains("apolog") || p.contains("sorry")) {
                    return "Subject: My Apologies\n\nDear " + r + ",\n\nI sincerely apologize for [issue]. I take full responsibility and have taken steps to make sure it does not happen again. "
                            + "Thank you for your understanding.\n\nBest regards,\n[Your name]";
                }
                return "Subject: [Topic]\n\nDear " + r + ",\n\nI am writing to [purpose]. [Add one or two supporting details]. "
                        + "Please let me know if you have any questions.\n\nThank you for your time.\n\nBest regards,\n[Your name]";
            }
        });

        Compute.reg("Poem Writer", new String[][]{fl("TOPIC", "e.g. rain, love, city night")}, "WRITE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String t = Compute.s(in, "f0");
                if (t.length() == 0) t = "the night";
                return "Ode to " + t + "\n\n" + t + " falls softly on the sleeping street,\n"
                        + "a neon hum where dark and daylight meet,\n"
                        + "each moment glows, then fades beyond recall,\n"
                        + "yet " + t + " remains the sweetest of them all.\n\n"
                        + "So let it come, let every whisper stay,\n"
                        + "for " + t + " turns the ordinary grey\n"
                        + "to gold.";
            }
        });

        Compute.reg("Story Writer", new String[][]{fl("TOPIC", "e.g. a lost phone, the last city")}, "WRITE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String t = Compute.s(in, "f0");
                if (t.length() == 0) t = "a quiet town";
                return t.toUpperCase(Locale.US) + "\n\n"
                        + "It began on an ordinary evening, the kind people forget by morning. But for Maya, " + t + " was never ordinary.\n\n"
                        + "She noticed the small things: a flickering street light, a stranger's hurried steps, a green glow where no light should be. "
                        + "By midnight, every small thing had become a clue, and every clue pointed to one impossible truth — " + t + " was hiding something.\n\n"
                        + "When she finally understood, the choice was simple: walk away and stay safe, or step into the dark and find out. "
                        + "Maya smiled, tightened her jacket, and stepped forward.\n\nTHE END (or is it?)";
            }
        });

        Compute.reg("Quote Maker", new String[][]{fl("TOPIC / MOOD", "e.g. success, discipline, calm")}, "GENERATE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String t = Compute.s(in, "f0").toLowerCase(Locale.US);
                if (t.contains("disci") || t.contains("habit")) return "\"Discipline is choosing between what you want now and what you want most.\"";
                if (t.contains("success") || t.contains("win")) return "\"Success is not final, failure is not fatal: it is the courage to continue that counts.\" — often attributed to W. Churchill";
                if (t.contains("calm") || t.contains("peace")) return "\"Peace comes from within. Do not seek it without.\" — Buddha (attributed)";
                if (t.contains("code") || t.contains("tech")) return "\"First, solve the problem. Then, write the code.\" — John Johnson";
                if (t.contains("time")) return "\"The two most powerful warriors are patience and time.\" — Tolstoy (attributed)";
                String[] arr = {"\"Small daily improvements are the key to staggering long-term results.\"",
                        "\"You don't need to be great to start, but you need to start to be great.\"",
                        "\"Energy and persistence conquer all things.\" — Benjamin Franklin (attributed)",
                        "\"Stay hungry, stay curious, stay kind.\"",
                        "\"The best way out is always through.\" — Robert Frost (attributed)"};
                return arr[RNG.nextInt(arr.length)];
            }
        });

        Compute.reg("Slogan Maker", new String[][]{fl("BRAND / PRODUCT", "e.g. coffee shop, phone brand")}, "GENERATE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String b = Compute.s(in, "f0");
                if (b.length() == 0) b = "your brand";
                return "1. " + b + " — Simply better.\n"
                        + "2. " + b + ": Built for you.\n"
                        + "3. Think " + b + ". Think smart.\n"
                        + "4. " + b + " — Feel the difference.\n"
                        + "5. Where " + b + " meets excellence.\n"
                        + "6. " + b + ". Every day. Every time.";
            }
        });

        Compute.reg("Resume Builder", new String[][]{fl("NAME", "Your name"), fl("ROLE", "Target role, e.g. Android Developer"),
                fl("SKILLS", "comma separated")}, "BUILD", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String name = Compute.s(in, "f0");
                if (name.length() == 0) name = "Your Name";
                String role = Compute.s(in, "f1");
                if (role.length() == 0) role = "Professional";
                String skills = Compute.s(in, "f2");
                if (skills.length() == 0) skills = "Communication, Teamwork, Problem solving";
                return "════════════════════════════\n" + name.toUpperCase(Locale.US)
                        + "\n" + role + "\n════════════════════════════\n\n"
                        + "PROFILE\nMotivated " + role + " with hands-on experience and a passion for quality. "
                        + "Quick learner, reliable teammate, focused on results.\n\n"
                        + "SKILLS\n" + skills.replace(", ", "\n") + "\n\n"
                        + "EXPERIENCE\n— [Job title], [Company] ([Year]–[Year])\n   • [Achievement with a number]\n   • [Achievement]\n\n"
                        + "EDUCATION\n— [Degree], [School], [Year]\n\n"
                        + "Fill the [brackets] with your details.";
            }
        });

        Compute.reg("Blog Outline", new String[][]{fl("TOPIC", "e.g. android development")}, "OUTLINE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String t = Compute.s(in, "f0");
                if (t.length() == 0) t = "your topic";
                return "TITLE: " + t + " — A Practical Guide\n\n"
                        + "H2: Why " + t + " matters in 2026\n  - Key trends\n  - Who should care\n\n"
                        + "H2: The basics in 5 minutes\n  - Core concepts\n  - Must-know terms\n\n"
                        + "H2: Step-by-step walkthrough\n  - Step 1: setup\n  - Step 2: first project\n  - Step 3: common pitfalls\n\n"
                        + "H2: Pro tips\n  - Shortcuts and tools\n  - Mistakes to avoid\n\n"
                        + "H2: Conclusion + call to action";
            }
        });

        Compute.reg("Product Description", new String[][]{fl("PRODUCT", "e.g. wireless earbuds")}, "WRITE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String p = Compute.s(in, "f0");
                if (p.length() == 0) p = "This product";
                return p + " — engineered for everyday life.\n\n"
                        + "✓ Premium build with a sleek, modern design\n"
                        + "✓ Long-lasting performance you can rely on\n"
                        + "✓ Easy setup — ready in under a minute\n"
                        + "✓ Backed by friendly support\n\n"
                        + "Whether at home, at work, or on the go, " + p + " fits seamlessly into your day. "
                        + "Order today and see why customers keep coming back.";
            }
        });

        Compute.reg("Study Notes Maker", new String[][]{ml("TEXT", "Paste lesson / topic text")}, "MAKE NOTES", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String v = ComputeText.nv(in.get("f0"));
                String[] sents = v.replace("\n", " ").split("(?<=[.!?])\\s+");
                StringBuilder sb = new StringBuilder("STUDY NOTES\n─────────────\n");
                int n = 1;
                for (String s : sents) {
                    s = s.trim();
                    if (s.length() < 15) continue;
                    // shorten: drop filler prefix
                    s = s.replaceAll("^(However|Moreover|In addition|Also),?\\s*", "");
                    sb.append(n++).append(". ").append(shorten(s)).append('\n');
                    if (n % 4 == 1 && sents.length > 6) sb.append('\n');
                }
                return sb.toString().trim();
            }
        });

        Compute.reg("Flashcard Maker", new String[][]{ml("CONTENT", "Lines of Q: ... A: ... or plain facts")}, "MAKE CARDS", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String[] lines = ComputeText.nv(in.get("f0")).split("\n");
                StringBuilder sb = new StringBuilder();
                int n = 1;
                for (String l : lines) {
                    l = l.trim();
                    if (l.length() == 0) continue;
                    if (l.toLowerCase(Locale.US).startsWith("q:")) {
                        sb.append("CARD ").append(n++).append("\nFRONT: ").append(l.substring(2).trim());
                    } else if (l.toLowerCase(Locale.US).startsWith("a:")) {
                        sb.append("\nBACK:  ").append(l.substring(2).trim()).append("\n\n");
                    } else {
                        sb.append("CARD ").append(n++).append("\nFRONT: ").append(shorten(l))
                                .append("\nBACK:  ").append(l).append("\n\n");
                    }
                }
                return sb.length() == 0 ? "(empty)" : sb.toString().trim();
            }
        });

        Compute.reg("Idea Brainstorm", new String[][]{fl("AREA", "e.g. app ideas, business, content")}, "BRAINSTORM", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String a = Compute.s(in, "f0");
                if (a.length() == 0) a = "ideas";
                return "10 " + a + ":\n\n"
                        + "1. Combine two favorites: [A] + [B] for a new audience\n"
                        + "2. Simplify something everyone finds complicated\n"
                        + "3. Localize a global trend for your community\n"
                        + "4. Bundle small services into one monthly pack\n"
                        + "5. Teach what you know in short video form\n"
                        + "6. Automate a boring task people do weekly\n"
                        + "7. Make a premium version of a free favorite\n"
                        + "8. Target an ignored group (beginners, elders, rural)\n"
                        + "9. Add a community layer: fans helping fans\n"
                        + "10. Do the opposite of what everyone else does";
            }
        });
    }

    static int count(String hay, String needle) {
        int c = 0, i;
        while ((i = hay.indexOf(needle)) >= 0) {
            c++;
            hay = hay.substring(i + needle.length());
        }
        return c;
    }

    static int countRegex(String hay, String re) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(re).matcher(hay);
        int c = 0;
        while (m.find()) c++;
        return c;
    }

    static String shorten(String s) {
        if (s.length() <= 110) return s;
        String cut = s.substring(0, 110);
        int sp = cut.lastIndexOf(' ');
        return cut.substring(0, sp < 60 ? 110 : sp) + "…";
    }
}
