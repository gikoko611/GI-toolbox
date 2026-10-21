package com.gikoko.gitoolbox;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Security & Privacy tools (23; File Checksum + App Lock = panels, Secure Notes = ListToolActivity). */
final class ComputeSecurity {

    private ComputeSecurity() {
    }

    static final String CHARS = "abcdefghijklmnopqrstuvwxyz";
    static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final String DIGITS = "0123456789";
    static final String SYMBOLS = "!@#$%^&*()-_=+[]{};:,.<>?/~";
    static final SecureRandom RNG = new SecureRandom();

    static String[] fl(String l, String h) {
        return new String[]{l, h};
    }

    static String[] ml(String l, String h) {
        return new String[]{l, h, "multi"};
    }

    static String hash(String algo, Map<String, String> in) throws Exception {
        String v = ComputeText.nv(in.get("f0"));
        MessageDigest md = MessageDigest.getInstance(algo);
        byte[] d = md.digest(v.getBytes(StandardCharsets.UTF_8));
        return algo + "('" + (v.length() > 40 ? v.substring(0, 40) + "…" : v) + "') =\n\n" + hex(d);
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static void register() {

        // ---- password generators ----
        Compute.reg("Password Generator", new String[][]{fl("LENGTH", "e.g. 16"),
                fl("OPTIONS", "upper / digits / symbols, e.g. uds (all) or d (digits only)")}, "GENERATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                int len = Compute.i(in, "f0");
                if (len < 4 || len > 64) throw new Exception("Length must be 4-64");
                String o = Compute.s(in, "f1").toLowerCase(Locale.US);
                String pool = CHARS;
                if (o.contains("u") || o.contains("a")) pool += UPPER;
                if (o.contains("d") || o.contains("a")) pool += DIGITS;
                if (o.contains("s") || o.contains("a")) pool += SYMBOLS;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < len; i++) sb.append(pool.charAt(RNG.nextInt(pool.length())));
                return "Password:\n" + sb + "\n\nLength: " + len + "\nEntropy: ~" + (len * (Math.log(pool.length()) / Math.log(2))) + " bits";
            }
        });

        Compute.reg("PIN Generator", new String[][]{fl("LENGTH", "4 - 12 digits"), fl("COUNT", "how many (blank = 5)")}, "GENERATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                int len = Compute.i(in, "f0");
                if (len < 4 || len > 12) throw new Exception("Length must be 4-12");
                int c = Compute.s(in, "f1").length() == 0 ? 5 : Compute.i(in, "f1");
                if (c < 1 || c > 20) throw new Exception("Count must be 1-20");
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < c; j++) {
                    for (int i = 0; i < len; i++) sb.append(DIGITS.charAt(RNG.nextInt(10)));
                    sb.append('\n');
                }
                return sb.toString().trim();
            }
        });

        Compute.reg("Passphrase Generator", new String[][]{fl("WORDS", "how many words, e.g. 4")}, "GENERATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                int n = Compute.i(in, "f0");
                if (n < 2 || n > 12) throw new Exception("Words must be 2-12");
                String[] words = {"neon", "cyber", "green", "gold", "dark", "matrix", "pixel", "quantum", "vector",
                        "logic", "spark", "delta", "cipher", "token", "orbit", "photon", "nexus", "zenith",
                        "polar", "echo", "flux", "grid", "helix", "ionic", "ko", "storm", "raven", "onyx"};
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    if (i > 0) sb.append('-');
                    sb.append(words[RNG.nextInt(words.length)]);
                }
                String p = sb.toString();
                return p + "\n\nLength: " + p.length();
            }
        });

        Compute.reg("Password Strength", new String[][]{fl("PASSWORD", "password to test (stays local)")}, "ANALYZE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String p = Compute.s(in, "f0");
                int score = 0;
                if (p.length() >= 8) score++;
                if (p.length() >= 12) score++;
                if (p.matches(".*[a-z].*")) score++;
                if (p.matches(".*[A-Z].*")) score++;
                if (p.matches(".*\\d.*")) score++;
                if (p.matches(".*[^A-Za-z0-9].*")) score++;
                String label = score <= 2 ? "WEAK" : score <= 4 ? "MEDIUM" : score <= 5 ? "STRONG" : "VERY STRONG";
                int bits = (int) (p.length() * 5.5);
                return "Strength: " + label + "  (" + score + "/6)\n"
                        + "Length: " + p.length() + " chars\nEstimated entropy: ~" + bits + " bits\n"
                        + (score <= 4 ? "Tip: add uppercase, digits and symbols." : "Good password!");
            }
        });

        // ---- hashes ----
        Compute.reg("MD5 Hash", new String[][]{ml("TEXT", "text to hash")}, "HASH", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                return hash("MD5", in);
            }
        });
        Compute.reg("SHA-1 Hash", new String[][]{ml("TEXT", "text to hash")}, "HASH", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                return hash("SHA-1", in);
            }
        });
        Compute.reg("SHA-256 Hash", new String[][]{ml("TEXT", "text to hash")}, "HASH", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                return hash("SHA-256", in);
            }
        });
        Compute.reg("SHA-512 Hash", new String[][]{ml("TEXT", "text to hash")}, "HASH", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                return hash("SHA-512", in);
            }
        });

        Compute.reg("HMAC Generator", new String[][]{fl("ALGO", "HmacSHA256 / HmacSHA1 / HmacMD5"),
                fl("KEY", "secret key"), ml("MESSAGE", "text")}, "GENERATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String algo = Compute.s(in, "f0");
                if (algo.length() == 0) algo = "HmacSHA256";
                Mac mac = Mac.getInstance(algo);
                mac.init(new SecretKeySpec(Compute.s(in, "f1").getBytes(StandardCharsets.UTF_8), algo));
                return algo + " HMAC:\n" + hex(mac.doFinal(ComputeText.nv(in.get("f2")).getBytes(StandardCharsets.UTF_8)));
            }
        });

        Compute.reg("Checksum Tool", new String[][]{fl("ALGO", "md5 / sha-1 / sha-256 / sha-512 (blank = sha-256)"),
                ml("TEXT", "text to checksum")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String a = Compute.s(in, "f0").toLowerCase(Locale.US).replace("-", "");
                if (a.length() == 0) a = "sha256";
                return hash(a.equals("md5") ? "MD5" : a.equals("sha1") ? "SHA-1" : a.equals("sha512") ? "SHA-512" : "SHA-256", in);
            }
        });

        Compute.reg("CRC32 Hash", new String[][]{ml("TEXT", "text")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                java.util.zip.CRC32 c = new java.util.zip.CRC32();
                byte[] b = ComputeText.nv(in.get("f0")).getBytes(StandardCharsets.UTF_8);
                c.update(b, 0, b.length);
                return "CRC32: " + Long.toHexString(c.getValue()).toUpperCase(Locale.US)
                        + "\nDec:    " + c.getValue();
            }
        });

        // ---- encodings ----
        Compute.reg("Base32 Encode", new String[][]{fl("MODE", "encode / decode"), ml("INPUT", "text or base32")}, "RUN", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                boolean enc = !Compute.s(in, "f0").toLowerCase(Locale.US).startsWith("d");
                String v = ComputeText.nv(in.get("f1"));
                if (enc) {
                    byte[] b = v.getBytes(StandardCharsets.UTF_8);
                    StringBuilder sb = new StringBuilder();
                    String A = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
                    for (int i = 0; i < b.length; i += 5) {
                        int n = Math.min(5, b.length - i);
                        long buf = 0;
                        for (int j = 0; j < 5; j++) buf = (buf << 8) | (j < n ? (b[i + j] & 0xFF) : 0);
                        for (int j = 0; j < (n * 8 + 4) / 5; j++) {
                            if (j > 0 && sb.length() % 8 == 0) sb.append('\n');
                            sb.append(A.charAt((int) ((buf >> (35 - j * 5)) & 31)));
                        }
                    }
                    return sb.toString();
                }
                String s = v.replace("\n", "").replace(" ", "").toUpperCase(Locale.US).replace("=", "");
                List<Byte> out = new ArrayList<>();
                long buf = 0;
                int bits = 0;
                String A = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
                for (char ch : s.toCharArray()) {
                    int idx = A.indexOf(ch);
                    if (idx < 0) continue;
                    buf = (buf << 5) | idx;
                    bits += 5;
                    if (bits >= 8) {
                        bits -= 8;
                        out.add((byte) ((buf >> bits) & 0xFF));
                    }
                }
                byte[] arr = new byte[out.size()];
                for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
                return new String(arr, StandardCharsets.UTF_8);
            }
        });

        // ---- AES ----
        Compute.reg("AES Encrypt", new String[][]{fl("PASSWORD", "password"), ml("TEXT", "plaintext")}, "ENCRYPT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                return aes(true, Compute.s(in, "f0"), ComputeText.nv(in.get("f1")));
            }
        });
        Compute.reg("AES Decrypt", new String[][]{fl("PASSWORD", "password"),
                ml("CIPHERTEXT", "output of AES Encrypt (base64)")}, "DECRYPT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                return aes(false, Compute.s(in, "f0"), ComputeText.nv(in.get("f1")));
            }
        });

        // ---- TOTP ----
        Compute.reg("TOTP Generator", new String[][]{fl("SECRET", "base32 secret, e.g. JBSWY3DPEHPK3PXP")}, "GENERATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String secret = Compute.s(in, "f0").toUpperCase(Locale.US).replace(" ", "");
                byte[] key = base32Decode(secret);
                long step = System.currentTimeMillis() / 1000 / 30;
                String code = totp(key, step);
                long remain = 30 - (System.currentTimeMillis() / 1000) % 30;
                return "TOTP: " + code + "\nValid: " + remain + "s\n\nPrev: " + totp(key, step - 1) + "\nNext: " + totp(key, step + 1);
            }
        });

        // ---- validators ----
        Compute.reg("Credit Card Validator", new String[][]{fl("CARD NUMBER", "digits, spaces ok")}, "VALIDATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String n = Compute.s(in, "f0").replace(" ", "").replace("-", "");
                if (!n.matches("\\d{8,19}")) throw new Exception("Enter 8-19 digits");
                int sum = 0;
                boolean alt = false;
                for (int i = n.length() - 1; i >= 0; i--) {
                    int d = n.charAt(i) - '0';
                    if (alt) {
                        d *= 2;
                        if (d > 9) d -= 9;
                    }
                    sum += d;
                    alt = !alt;
                }
                String type = n.startsWith("4") ? "Visa" : n.matches("5[1-5].*") ? "Mastercard"
                        : n.matches("3[47].*") ? "American Express" : n.matches("6(?:011|5).*") ? "Discover"
                        : n.matches("3(?:0[0-5]|[68]).*") ? "Diners Club" : n.matches("606282|38.*)") ? "Unknown" : "Unknown";
                boolean ok = sum % 10 == 0;
                return "Luhn check: " + (ok ? "VALID ✓" : "INVALID ✗") + "\nType: " + type + "\nLength: " + n.length();
            }
        });

        Compute.reg("IBAN Validator", new String[][]{fl("IBAN", "e.g. DE89370400440532013000")}, "VALIDATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String ib = Compute.s(in, "f0").replace(" ", "").toUpperCase(Locale.US);
                if (!ib.matches("[A-Z]{2}\\d{2}[A-Z0-9]{10,30}")) throw new Exception("Bad IBAN format");
                String rearr = ib.substring(4) + ib.substring(0, 4);
                StringBuilder num = new StringBuilder();
                for (char c : rearr.toCharArray()) {
                    if (Character.isLetter(c)) num.append(c - 'A' + 10);
                    else num.append(c);
                }
                long rem = 0;
                for (int i = 0; i < num.length(); i++) {
                    rem = (rem * 10 + (num.charAt(i) - '0')) % 97;
                }
                return "IBAN: " + ib + "\nChecksum mod 97: " + rem + " → " + (rem == 1 ? "VALID ✓" : "INVALID ✗")
                        + "\nCountry: " + ib.substring(0, 2);
            }
        });

        Compute.reg("UUID Generator", new String[][]{fl("COUNT", "how many (blank = 5)")}, "GENERATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                int c = Compute.s(in, "f0").length() == 0 ? 5 : Compute.i(in, "f0");
                if (c < 1 || c > 50) throw new Exception("Count must be 1-50");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < c; i++) sb.append(UUID.randomUUID().toString()).append(i < c - 1 ? '\n' : "");
                return sb.toString();
            }
        });

        Compute.reg("Random Bytes", new String[][]{fl("COUNT", "bytes, e.g. 32"), fl("FORMAT", "hex / b64 (blank = hex)")}, "GENERATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                int n = Compute.i(in, "f0");
                if (n < 1 || n > 1024) throw new Exception("Count must be 1-1024");
                byte[] b = new byte[n];
                RNG.nextBytes(b);
                if (Compute.s(in, "f1").startsWith("b")) return Base64.getEncoder().encodeToString(b);
                return hex(b);
            }
        });

        Compute.reg("Crypto Key Generator", new String[][]{fl("BITS", "128 / 192 / 256 (blank = 256)")}, "GENERATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                int bits = Compute.s(in, "f0").length() == 0 ? 256 : Compute.i(in, "f0");
                if (bits != 128 && bits != 192 && bits != 256) throw new Exception("Bits must be 128, 192 or 256");
                byte[] b = new byte[bits / 8];
                RNG.nextBytes(b);
                return "Key (" + bits + "-bit):\n\nHex:     " + hex(b)
                        + "\n\nBase64:  " + Base64.getEncoder().encodeToString(b);
            }
        });

        Compute.reg("Text Obfuscator", new String[][]{ml("TEXT", "text to obfuscate")}, "OBFUSCATE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String v = ComputeText.nv(in.get("f0"));
                StringBuilder sb = new StringBuilder();
                for (char c : v.toCharArray()) {
                    if (c >= 'a' && c <= 'z') sb.append((char) ('a' + (c - 'a' + 13) % 26));
                    else if (c >= 'A' && c <= 'Z') sb.append((char) ('A' + (c - 'A' + 13) % 26));
                    else sb.append(c);
                }
                return sb + "\n\n(ROT13 — run again to reverse)";
            }
        });

        Compute.reg("Subnet Calculator", new String[][]{fl("IP/CIDR", "e.g. 192.168.1.10/24")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String v = Compute.s(in, "f0");
                String ip = v, maskStr = null;
                if (v.contains("/")) {
                    String[] p = v.split("/");
                    ip = p[0];
                    maskStr = p[1];
                }
                String[] o = ip.split("\\.");
                if (o.length != 4) throw new Exception("Use IPv4, e.g. 192.168.1.10/24");
                int mask = maskStr != null ? Integer.parseInt(maskStr) : 24;
                if (mask < 0 || mask > 32) throw new Exception("CIDR must be 0-32");
                long addr = 0;
                for (String s : o) addr = (addr << 8) | (Integer.parseInt(s) & 0xFF);
                long netMask = mask == 0 ? 0 : (0xFFFFFFFFL << (32 - mask)) & 0xFFFFFFFFL;
                long net = addr & netMask;
                long bc = net | (~netMask & 0xFFFFFFFFL);
                int hosts = mask >= 31 ? (int) (Math.pow(2, 32 - mask)) : (int) (Math.pow(2, 32 - mask) - 2);
                return "Address:  " + ipTo(addr) + "\nNetmask:  " + ipTo(netMask)
                        + "\nNetwork:  " + ipTo(net) + "/" + mask + "\nBroadcast: " + ipTo(bc)
                        + "\nFirst host: " + ipTo(mask >= 31 ? net : net + 1)
                        + "\nLast host:  " + ipTo(mask >= 31 ? bc : bc - 1)
                        + "\nHosts:     " + hosts;
            }
        });

        Compute.reg("URL Encoder", new String[][]{fl("MODE", "encode / decode (blank = encode)"), ml("INPUT", "text or URL")}, "RUN", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                boolean dec = Compute.s(in, "f0").toLowerCase(Locale.US).startsWith("d");
                String v = ComputeText.nv(in.get("f1"));
                return dec ? java.net.URLDecoder.decode(v, "UTF-8") : java.net.URLEncoder.encode(v, "UTF-8").replace("+", "%20");
            }
        });

        Compute.reg("URL Decoder", new String[][]{ml("INPUT", "encoded URL/text")}, "DECODE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                return java.net.URLDecoder.decode(ComputeText.nv(in.get("f0")), "UTF-8");
            }
        });

        Compute.reg("Email Validator", new String[][]{ml("EMAILS", "one or more emails, one per line")}, "VALIDATE", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String[] lines = ComputeText.nv(in.get("f0")).split("\n");
                StringBuilder sb = new StringBuilder();
                for (String l : lines) {
                    String e = l.trim();
                    if (e.length() == 0) continue;
                    boolean ok = e.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
                    sb.append(ok ? "✓ " : "✗ ").append(e).append(ok ? "  valid" : "  INVALID").append('\n');
                }
                return sb.toString().trim();
            }
        });

        Compute.reg("Bandwidth Calc", new String[][]{fl("FILE SIZE", "size with unit, e.g. 700 MB"),
                fl("SPEED", "with unit, e.g. 10 Mbps")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double size = parseSize(Compute.s(in, "f0"));
                double speed = parseSize(Compute.s(in, "f1")) / 8.0; // bytes per second
                if (speed <= 0) throw new Exception("Enter speed, e.g. 10 Mbps");
                double sec = size / speed;
                return "Time: " + dur(sec) + "\n(" + Ui.fmt(size / 1e6, 1) + " MB at " + Compute.s(in, "f1") + ")";
            }
        });

        Compute.reg("HTTP Status Codes", new String[][]{fl("CODE OR QUERY", "e.g. 404, 503, 3xx, or 'redirect'")}, "LOOKUP", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String q = Compute.s(in, "f0").toLowerCase(Locale.US);
                String[][] codes = {
                        {"100", "Continue"}, {"101", "Switching Protocols"},
                        {"200", "OK"}, {"201", "Created"}, {"202", "Accepted"}, {"204", "No Content"},
                        {"301", "Moved Permanently"}, {"302", "Found (redirect)"}, {"304", "Not Modified"},
                        {"307", "Temporary Redirect"}, {"308", "Permanent Redirect"},
                        {"400", "Bad Request"}, {"401", "Unauthorized"}, {"403", "Forbidden"},
                        {"404", "Not Found"}, {"405", "Method Not Allowed"}, {"408", "Request Timeout"},
                        {"409", "Conflict"}, {"410", "Gone"}, {"413", "Payload Too Large"},
                        {"415", "Unsupported Media Type"}, {"418", "I'm a teapot"},
                        {"429", "Too Many Requests"}, {"451", "Unavailable For Legal Reasons"},
                        {"500", "Internal Server Error"}, {"501", "Not Implemented"},
                        {"502", "Bad Gateway"}, {"503", "Service Unavailable"},
                        {"504", "Gateway Timeout"}, {"511", "Network Authentication Required"}
                };
                StringBuilder sb = new StringBuilder();
                if (q.matches("\\d{3}")) {
                    for (String[] c : codes) if (c[0].equals(q)) return q + " " + c[1];
                    return q + " is not a standard code";
                }
                if (q.endsWith("xx")) {
                    for (String[] c : codes) if (c[0].startsWith(q.substring(0, 1))) sb.append(c[0]).append("  ").append(c[1]).append('\n');
                    return sb.toString().trim();
                }
                for (String[] c : codes) if (c[1].toLowerCase(Locale.US).contains(q)) sb.append(c[0]).append("  ").append(c[1]).append('\n');
                return sb.length() == 0 ? "No match found" : sb.toString().trim();
            }
        });

        Compute.reg("Privacy Cleaner", new String[][]{fl("CONFIRM", "type YES to see cleanup steps")}, "CLEAN", new Compute.Fn() {
            public String run(Map<String, String> in) {
                String c = Compute.s(in, "f0");
                if (!c.equalsIgnoreCase("yes")) return "Type YES to confirm.";
                return "Cleanup steps for this device:\n\n"
                        + "1. Settings → Apps → GIToolbox → Clear data\n"
                        + "2. Clear browser history & cookies\n"
                        + "3. Delete downloaded files not needed\n"
                        + "4. Reset advertising ID: Settings → Google → Ads\n\n"
                        + "Note: notes, secure notes and QR history stored by this app are wiped by step 1.";
            }
        });
    }

    // ---- AES-GCM with PBKDF2 password key ----
    static String aes(boolean encrypt, String password, String text) throws Exception {
        byte[] salt = new byte[16];
        if (encrypt) RNG.nextBytes(salt);
        javax.crypto.SecretKeyFactory kf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        javax.crypto.SecretKey key = kf.generateSecret(new javax.crypto.spec.PBEKeySpec(
                password.toCharArray(), salt, 60000, 256));
        byte[] aesKey = key.getEncoded();
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        if (encrypt) {
            byte[] iv = new byte[12];
            RNG.nextBytes(iv);
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(text.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[16 + 12 + ct.length];
            System.arraycopy(salt, 0, out, 0, 16);
            System.arraycopy(iv, 0, out, 16, 12);
            System.arraycopy(ct, 0, out, 28, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } else {
            byte[] all = Base64.getDecoder().decode(text.trim());
            if (all.length < 28) throw new Exception("Invalid ciphertext");
            System.arraycopy(all, 0, salt, 0, 16);
            byte[] iv = new byte[12];
            System.arraycopy(all, 16, iv, 0, 12);
            javax.crypto.SecretKey dk = kf.generateSecret(new javax.crypto.spec.PBEKeySpec(
                    password.toCharArray(), salt, 60000, 256));
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dk.getEncoded(), "AES"), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(all, 28, all.length - 28), StandardCharsets.UTF_8);
        }
    }

    static byte[] base32Decode(String s) throws Exception {
        String A = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        s = s.replace("=", "").replace(" ", "");
        List<Byte> out = new ArrayList<>();
        long buf = 0;
        int bits = 0;
        for (char ch : s.toCharArray()) {
            int idx = A.indexOf(ch);
            if (idx < 0) throw new Exception("Invalid base32 char: " + ch);
            buf = (buf << 5) | idx;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out.add((byte) ((buf >> bits) & 0xFF));
            }
        }
        byte[] r = new byte[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    static String totp(byte[] key, long step) throws Exception {
        byte[] msg = new byte[8];
        for (int i = 7; i >= 0; i--) {
            msg[i] = (byte) (step & 0xFF);
            step >>= 8;
        }
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] h = mac.doFinal(msg);
        int off = h[h.length - 1] & 0xF;
        int bin = ((h[off] & 0x7F) << 24) | ((h[off + 1] & 0xFF) << 16) | ((h[off + 2] & 0xFF) << 8) | (h[off + 3] & 0xFF);
        return String.format(Locale.US, "%06d", bin % 1000000);
    }

    static double parseSize(String v) throws Exception {
        v = v.toLowerCase(Locale.US).replace(" ", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9.]+)\\s*([a-z/]+)").matcher(v);
        if (!m.matches()) throw new Exception("Use format like: 700 MB or 10 Mbps");
        double n = Double.parseDouble(m.group(1));
        String u = m.group(2);
        double mult;
        if (u.equals("b") || u.equals("byte") || u.equals("bytes")) mult = 1;
        else if (u.equals("kb")) mult = 1e3;
        else if (u.equals("mb")) mult = 1e6;
        else if (u.equals("gb")) mult = 1e9;
        else if (u.equals("tb")) mult = 1e12;
        else if (u.equals("kib")) mult = 1024;
        else if (u.equals("mib")) mult = 1048576;
        else if (u.equals("gib")) mult = 1073741824;
        else if (u.equals("bit") || u.equals("bits")) mult = 0.125;
        else if (u.equals("kbps") || u.equals("kbit/s")) mult = 125;
        else if (u.equals("mbps") || u.equals("mbit/s")) mult = 125000;
        else if (u.equals("gbps")) mult = 125000000;
        else throw new Exception("Unknown unit: " + u);
        return n * mult;
    }

    static String dur(double sec) {
        if (sec < 60) return Ui.fmt(sec, 1) + " sec";
        if (sec < 3600) return (int) (sec / 60) + " min " + (int) (sec % 60) + " sec";
        if (sec < 86400) return (int) (sec / 3600) + " h " + (int) ((sec % 3600) / 60) + " min";
        return Ui.fmt(sec / 86400, 1) + " days";
    }

    static String ipTo(long v) {
        return (v >> 24 & 0xFF) + "." + (v >> 16 & 0xFF) + "." + (v >> 8 & 0xFF) + "." + (v & 0xFF);
    }
}
