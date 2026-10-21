package com.gikoko.gitoolbox;

import android.app.Activity;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

/** Network & IP tools — all requests run on background threads. v3.0: +10 tools. */
public class NetActivity extends Activity {

    private String tool;
    private EditText[] fields;
    private TextView result;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tool = getIntent().getStringExtra("tool");
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.header(this, tool, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));

        String[][] spec = formSpec();
        LinearLayout card = Ui.card(this);
        fields = new EditText[spec.length];
        for (int i = 0; i < spec.length; i++) {
            if (spec[i][0].length() > 0) card.addView(Ui.text(this, spec[i][0], 11, Ui.GREEN, true));
            EditText e = Ui.input(this, spec[i][1], false);
            card.addView(e);
            fields[i] = e;
        }
        root.addView(card);

        Button run = Ui.button(this, "⚡ RUN");
        run.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTool();
            }
        });
        root.addView(run);

        LinearLayout outCard = Ui.card(this);
        result = Ui.text(this, "", 13, Ui.TEXT, false);
        result.setTypeface(android.graphics.Typeface.MONOSPACE);
        result.setTextIsSelectable(true);
        outCard.addView(Ui.text(this, "RESULT", 11, Ui.DIM, true));
        outCard.addView(result);
        root.addView(outCard);
        setContentView(Ui.scroll(this, root));
        if (tool.equals("My IP Address") || tool.equals("WiFi Analyzer")) runTool();
    }

    String val(int i) {
        return i < fields.length ? fields[i].getText().toString().trim() : "";
    }

    String[][] formSpec() {
        if (tool.equals("IP Lookup") || tool.equals("Geo IP Map")) return new String[][]{{"IP ADDRESS", "leave blank for your own IP"}};
        if (tool.equals("DNS Lookup")) return new String[][]{{"DOMAIN", "e.g. github.com"}};
        if (tool.equals("Ping Tool")) return new String[][]{{"HOST", "e.g. 8.8.8.8 or github.com"}};
        if (tool.equals("HTTP Headers")) return new String[][]{{"URL", "e.g. https://github.com"}};
        if (tool.equals("SSL Checker")) return new String[][]{{"HOST", "e.g. github.com"}};
        if (tool.equals("WHOIS Lookup")) return new String[][]{{"DOMAIN", "e.g. example.com"}};
        if (tool.equals("Wake on LAN")) return new String[][]{{"MAC ADDRESS", "e.g. AA:BB:CC:DD:EE:FF"}, {"IP / BROADCAST", "blank = 255.255.255.255"}};
        if (tool.equals("Currency Rates")) return new String[][]{{"BASE CURRENCY", "e.g. USD (blank = USD)"}, {"AMOUNT", "blank = 1"}};
        if (tool.equals("Speed Test")) return new String[][]{{"MB TO DOWNLOAD", "blank = 5"}};
        if (tool.equals("Port Scanner")) return new String[][]{{"HOST", "e.g. 192.168.1.1"}, {"PORTS", "blank = common, or 80,443 or 1-200"}};
        if (tool.equals("IP Range Scanner")) return new String[][]{{"START IP", "e.g. 192.168.1.1"}, {"COUNT", "how many IPs (max 32, blank = 16)"}};
        if (tool.equals("MAC Lookup")) return new String[][]{{"MAC ADDRESS", "e.g. AA:BB:CC:DD:EE:FF"}};
        if (tool.equals("MX Record Lookup")) return new String[][]{{"DOMAIN", "e.g. gmail.com"}};
        if (tool.equals("DNS Propagation")) return new String[][]{{"DOMAIN", "e.g. github.com"}};
        if (tool.equals("Proxy Checker")) return new String[][]{{"PROXY", "host:port e.g. 1.2.3.4:8080"}, {"TEST URL", "blank = https://api.ipify.org"}};
        if (tool.equals("Trace Route")) return new String[][]{{"HOST", "e.g. github.com"}};
        if (tool.equals("Uptime Monitor")) return new String[][]{{"URL", "e.g. https://github.com"}, {"CHECKS", "how many checks (blank = 3)"}};
        if (tool.equals("Text Translator")) return new String[][]{{"TEXT", "Text to translate", "multi"}, {"PAIR", "en|my (from|to, blank = en|my)"}};
        return new String[][]{}; // My IP Address / WiFi Analyzer need no input
    }

    void runTool() {
        result.setText("⏳ Working…");
        final Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    final String out = execute();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            result.setText(out);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            result.setText("Error: " + e.getMessage()
                                    + "\n\nCheck your internet connection.");
                        }
                    });
                }
            }
        };
        t.start();
    }

    String execute() throws Exception {
        if (tool.equals("My IP Address")) {
            String ip = httpGet("https://api.ipify.org");
            return "YOUR PUBLIC IP:\n\n" + ip
                    + "\n\n(Shows the IP your internet provider uses)";
        }
        if (tool.equals("IP Lookup") || tool.equals("Geo IP Map")) {
            String ip = val(0);
            String json = httpGet("https://ipwho.is/" + (ip.length() == 0 ? "" : ip));
            org.json.JSONObject o = new org.json.JSONObject(json);
            if (!o.optBoolean("success", false)) return "Lookup failed: " + o.optString("message", "unknown IP");
            double lat = o.optDouble("latitude"), lon = o.optDouble("longitude");
            org.json.JSONObject conn = o.optJSONObject("connection");
            StringBuilder sb = new StringBuilder();
            sb.append("IP:        ").append(o.optString("ip")).append('\n')
                    .append("TYPE:      ").append(o.optString("type")).append('\n')
                    .append("COUNTRY:   ").append(o.optString("country")).append(" (").append(o.optString("country_code")).append(")\n")
                    .append("REGION:    ").append(o.optString("region")).append('\n')
                    .append("CITY:      ").append(o.optString("city")).append('\n')
                    .append("LAT/LON:   ").append(lat).append(", ").append(lon).append('\n')
                    .append("ISP:       ").append(conn == null ? "?" : conn.optString("isp", "?")).append('\n')
                    .append("ORG:       ").append(conn == null ? "?" : conn.optString("org", "?"));
            if (tool.equals("Geo IP Map")) {
                sb.append("\n\nMAP LINK:\nhttps://maps.google.com/?q=").append(lat).append(',').append(lon);
            }
            return sb.toString();
        }
        if (tool.equals("DNS Lookup")) {
            String host = val(0);
            InetAddress[] addrs = InetAddress.getAllByName(host);
            StringBuilder sb = new StringBuilder("DNS RECORDS for " + host + ":\n\n");
            int n = 1;
            for (InetAddress a : addrs) {
                sb.append(n++).append(". ").append(a.getHostAddress()).append('\n');
            }
            return sb.toString();
        }
        if (tool.equals("Ping Tool")) {
            String host = val(0);
            try {
                Process p = Runtime.getRuntime().exec("/system/bin/ping -c 4 -W 2 " + host);
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
                p.waitFor();
                return sb.toString().trim();
            } catch (Exception pingFail) {
                InetAddress a = InetAddress.getByName(host);
                long t0 = System.currentTimeMillis();
                boolean ok = a.isReachable(3000);
                long ms = System.currentTimeMillis() - t0;
                return host + (ok ? " is reachable (" + ms + " ms)" : " — no reply");
            }
        }
        if (tool.equals("Port Scanner")) return portScan();
        if (tool.equals("IP Range Scanner")) return rangeScan();
        if (tool.equals("MAC Lookup")) {
            String mac = val(0);
            if (mac.length() < 7) throw new Exception("Enter a MAC address");
            String resp = httpGet("https://api.macvendors.com/" + java.net.URLEncoder.encode(mac, "UTF-8"));
            if (resp.contains("macvendors") || resp.toLowerCase(Locale.US).contains("not found")
                    || resp.toLowerCase(Locale.US).contains("invalid")) {
                return "MAC " + mac.toUpperCase(Locale.US) + "\n\nVendor not found in the registry.";
            }
            return "MAC:       " + mac.toUpperCase(Locale.US) + "\nVENDOR:    " + resp
                    + "\n\n(Source: macvendors.com public registry)";
        }
        if (tool.equals("MX Record Lookup")) {
            String domain = val(0);
            org.json.JSONObject o = new org.json.JSONObject(
                    httpGet("https://dns.google/resolve?name=" + java.net.URLEncoder.encode(domain, "UTF-8") + "&type=MX"));
            org.json.JSONArray ans = o.optJSONArray("Answer");
            StringBuilder sb = new StringBuilder("MAIL SERVERS (MX) for " + domain + ":\n\n");
            if (ans == null || ans.length() == 0) {
                sb.append("No MX records found.");
            } else {
                for (int i = 0; i < ans.length(); i++) {
                    sb.append(i + 1).append(". ").append(ans.getJSONObject(i).optString("data")).append('\n');
                }
                sb.append("\nMail for this domain is delivered to the servers above\n(lowest priority number first).");
            }
            return sb.toString().trim();
        }
        if (tool.equals("DNS Propagation")) {
            String domain = val(0);
            String q = java.net.URLEncoder.encode(domain, "UTF-8");
            Set<String> google = dnsJson("https://dns.google/resolve?name=" + q + "&type=A");
            Set<String> cloud = dnsJson("https://cloudflare-dns.com/dns-query?name=" + q + "&type=A");
            StringBuilder sb = new StringBuilder("DNS PROPAGATION CHECK: " + domain + "\n\n");
            sb.append("Google DNS:\n");
            for (String s : google) sb.append("  ").append(s).append('\n');
            if (google.isEmpty()) sb.append("  (no A records)\n");
            sb.append("\nCloudflare DNS:\n");
            for (String s : cloud) sb.append("  ").append(s).append('\n');
            if (cloud.isEmpty()) sb.append("  (no A records)\n");
            boolean match = !google.isEmpty() && google.equals(cloud);
            sb.append("\n").append(match ? "✓ Both resolvers agree — record looks propagated."
                    : "⚠ Results differ — the record may still be propagating (or one resolver is cached).");
            return sb.toString().trim();
        }
        if (tool.equals("Proxy Checker")) return proxyCheck();
        if (tool.equals("Trace Route")) return traceRoute();
        if (tool.equals("Uptime Monitor")) return uptime();
        if (tool.equals("WiFi Analyzer")) return wifiInfo();
        if (tool.equals("Text Translator")) {
            String text = val(0);
            String pair = val(1).length() == 0 ? "en|my" : val(1);
            if (text.length() == 0) throw new Exception("Enter text to translate");
            if (!pair.contains("|")) pair = "en|" + pair;
            String u = "https://api.mymemory.translated.net/get?q=" + java.net.URLEncoder.encode(text, "UTF-8")
                    + "&langpair=" + java.net.URLEncoder.encode(pair, "UTF-8");
            org.json.JSONObject o = new org.json.JSONObject(httpGet(u));
            org.json.JSONObject rd = o.optJSONObject("responseData");
            String tr = rd == null ? "" : rd.optString("translatedText", "");
            if (tr.length() == 0) throw new Exception("Translation service returned nothing — check the pair (e.g. en|my)");
            return "TRANSLATION " + pair + ":\n\n" + tr
                    + "\n\n—\n" + text + "\n\n(Free MyMemory translation engine)";
        }
        if (tool.equals("HTTP Headers")) {
            String u = val(0);
            if (!u.startsWith("http")) u = "https://" + u;
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setRequestMethod("HEAD");
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            c.connect();
            StringBuilder sb = new StringBuilder();
            sb.append("STATUS: ").append(c.getResponseCode()).append(' ').append(c.getResponseMessage()).append("\n\n");
            Map<String, java.util.List<String>> hs = c.getHeaderFields();
            for (Map.Entry<String, java.util.List<String>> e : hs.entrySet()) {
                String key = e.getKey();
                if (key == null) continue;
                sb.append(key).append(": ");
                for (String v : e.getValue()) sb.append(v);
                sb.append('\n');
            }
            c.disconnect();
            return sb.toString().trim();
        }
        if (tool.equals("SSL Checker")) {
            String host = val(0);
            HttpsURLConnection c = (HttpsURLConnection) new URL("https://" + host).openConnection();
            c.setConnectTimeout(10000);
            c.connect();
            Certificate[] certs = c.getServerCertificates();
            StringBuilder sb = new StringBuilder("SSL CERTIFICATE for " + host + ":\n\n");
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            for (Certificate cert : certs) {
                if (cert instanceof X509Certificate) {
                    X509Certificate x = (X509Certificate) cert;
                    sb.append("SUBJECT:  ").append(x.getSubjectX500Principal().getName()).append('\n')
                            .append("ISSUER:   ").append(x.getIssuerX500Principal().getName()).append('\n')
                            .append("VALID FROM:  ").append(f.format(x.getNotBefore())).append('\n')
                            .append("VALID UNTIL: ").append(f.format(x.getNotAfter())).append('\n');
                    long days = (x.getNotAfter().getTime() - System.currentTimeMillis()) / 86400000L;
                    sb.append(days > 0 ? "EXPIRES IN:  " + days + " days ✓" : "❌ EXPIRED " + (-days) + " days ago").append('\n');
                }
            }
            c.disconnect();
            return sb.toString().trim();
        }
        if (tool.equals("WHOIS Lookup")) {
            String domain = val(0);
            String raw = whois("whois.iana.org", domain);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)whois:\\s*(\\S+)").matcher(raw);
            if (m.find()) {
                String server = m.group(1);
                String real = whois(server, domain);
                if (real.contains("No match") || real.trim().length() == 0) return raw;
                return "Server: " + server + "\n\n" + trim(real, 2500);
            }
            return trim(raw, 2500);
        }
        if (tool.equals("Wake on LAN")) {
            String mac = val(0).replace(":", "").replace("-", "");
            String ip = val(1).length() == 0 ? "255.255.255.255" : val(1);
            if (mac.length() != 12 || !mac.matches("[0-9a-fA-F]+")) throw new Exception("MAC must be 12 hex digits");
            byte[] packet = new byte[102];
            for (int i = 0; i < 6; i++) packet[i] = (byte) 0xFF;
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 6; j++) {
                    packet[6 + i * 6 + j] = (byte) Integer.parseInt(mac.substring(j * 2, j * 2 + 2), 16);
                }
            }
            DatagramSocket s = new DatagramSocket();
            s.setBroadcast(true);
            DatagramPacket p = new DatagramPacket(packet, packet.length,
                    InetAddress.getByName(ip), 9);
            s.send(p);
            s.close();
            return "Magic packet sent ✓\nMAC: " + mac.toUpperCase(Locale.US) + "\nTo: " + ip
                    + "\n\nIf the target PC has Wake-on-LAN enabled in BIOS, it should wake now.";
        }
        if (tool.equals("Currency Rates")) {
            String base = val(0).length() == 0 ? "USD" : val(0).toUpperCase(Locale.US);
            String amt = val(1).length() == 0 ? "1" : val(1);
            String json = httpGet("https://open.er-api.com/v6/latest/" + base);
            org.json.JSONObject o = new org.json.JSONObject(json);
            if (!o.optBoolean("result", false)) throw new Exception("Unknown currency: " + base);
            double amount = Double.parseDouble(amt);
            org.json.JSONObject rates = o.getJSONObject("rates");
            StringBuilder sb = new StringBuilder();
            sb.append(amount).append(' ').append(base).append(" equals:\n\n");
            String[] wanted = {"USD", "EUR", "MMK", "GBP", "JPY", "CNY", "THB", "SGD", "INR", "KRW", "AUD"};
            for (String w : wanted) {
                if (w.equals(base)) continue;
                if (rates.has(w)) {
                    sb.append(String.format(Locale.US, "%-4s %s", w, Ui.fmt(rates.getDouble(w) * amount, 2))).append('\n');
                }
            }
            sb.append("\nUpdated: ").append(o.optString("time_last_update_utc", ""));
            return sb.toString().trim();
        }
        if (tool.equals("Speed Test")) {
            double mb = val(0).length() == 0 ? 5 : Double.parseDouble(val(0));
            if (mb < 1 || mb > 25) mb = 5;
            URL u = new URL("https://speed.cloudflare.com/__down?bytes=" + (long) (mb * 1000000));
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(30000);
            InputStream in = c.getInputStream();
            byte[] buf = new byte[16384];
            long total = 0, t0 = System.currentTimeMillis();
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
            }
            double secs = (System.currentTimeMillis() - t0) / 1000.0;
            in.close();
            c.disconnect();
            double mbps = total * 8 / secs / 1e6;
            return "DOWNLOAD SPEED TEST\n\nDownloaded: " + Compute.fmtBytes(total)
                    + "\nTime:       " + Ui.fmt(secs, 2) + " s"
                    + "\n\nSpeed: ⚡ " + Ui.fmt(mbps, 2) + " Mbps  (" + Ui.fmt(mbps / 8, 2) + " MB/s)";
        }
        throw new Exception("Unknown tool");
    }

    // ------------------------------------------------------------- new v3 tools

    static final int[] COMMON_PORTS = {21, 22, 23, 25, 53, 80, 110, 135, 139, 143, 443, 445,
            3306, 3389, 5432, 5554, 5900, 6379, 8080, 8443, 8888, 9000, 17500, 32400};
    static final String[] PORT_NAMES = {"FTP", "SSH", "Telnet", "SMTP", "DNS", "HTTP", "POP3", "MSRPC",
            "NetBIOS", "IMAP", "HTTPS", "SMB", "MySQL", "RDP", "PostgreSQL", "DMCP", "VNC", "Redis",
            "HTTP-alt", "HTTPS-alt", "HTTP-alt2", "SonarQube", "Dropbox LAN", "Plex"};

    String portScan() throws Exception {
        String host = val(0);
        if (host.length() == 0) throw new Exception("Enter a host");
        final java.util.List<Integer> ports = new java.util.ArrayList<Integer>();
        String spec = val(1);
        if (spec.length() == 0) {
            for (int p : COMMON_PORTS) ports.add(p);
        } else if (spec.contains("-")) {
            String[] r = spec.split("-");
            int a = Integer.parseInt(r[0].trim());
            int b = Integer.parseInt(r[1].trim());
            if (b - a > 255) b = a + 255;
            for (int p = Math.max(1, a); p <= Math.min(65535, b); p++) ports.add(p);
        } else {
            for (String s : spec.split(",")) {
                try {
                    ports.add(Integer.parseInt(s.trim()));
                } catch (Exception ignore) {
                }
            }
            if (ports.isEmpty()) throw new Exception("No valid ports");
        }
        final java.util.Set<Integer> open = java.util.Collections.synchronizedSet(
                new java.util.LinkedHashSet<Integer>());
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(16, ports.size()));
        for (final int p : ports) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket s = new Socket();
                        s.connect(new InetSocketAddress(host, p), 400);
                        s.close();
                        open.add(p);
                    } catch (Exception ignore) {
                    }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);
        StringBuilder sb = new StringBuilder("PORT SCAN: " + host + "  (" + ports.size() + " ports)\n\n");
        if (open.isEmpty()) {
            sb.append("No open ports found in the scanned range.");
        } else {
            sb.append("OPEN:\n");
            java.util.List<Integer> sorted = new java.util.ArrayList<Integer>(open);
            java.util.Collections.sort(sorted);
            for (int p : sorted) {
                String name = "";
                for (int i = 0; i < COMMON_PORTS.length; i++) {
                    if (COMMON_PORTS[i] == p) name = PORT_NAMES[i];
                }
                sb.append("  ✓ ").append(p).append(name.length() > 0 ? "  (" + name + ")" : "").append('\n');
            }
            sb.append("\nClosed/filtered ports are not listed.");
        }
        return sb.toString().trim();
    }

    String rangeScan() throws Exception {
        String start = val(0);
        if (!start.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) throw new Exception("Start IP like 192.168.1.1");
        int count = 16;
        try {
            count = Integer.parseInt(val(1));
        } catch (Exception ignore) {
        }
        if (count < 1) count = 1;
        if (count > 32) count = 32;
        String[] p = start.split("\\.");
        long base = (Long.parseLong(p[0]) << 24) | (Long.parseLong(p[1]) << 16)
                | (Long.parseLong(p[2]) << 8) | Long.parseLong(p[3]);
        StringBuilder sb = new StringBuilder("PING SWEEP from " + start + " (" + count + " addresses)\n\n");
        int alive = 0;
        for (int i = 0; i < count; i++) {
            long a = base + i;
            String ip = ((a >> 24) & 255) + "." + ((a >> 16) & 255) + "." + ((a >> 8) & 255) + "." + (a & 255);
            boolean ok = pingOnce(ip);
            sb.append(String.format(Locale.US, "%-16s %s", ip, ok ? "● ALIVE" : "  no reply")).append('\n');
            if (ok) alive++;
        }
        sb.append("\n").append(alive).append(" of ").append(count).append(" hosts responded.");
        return sb.toString().trim();
    }

    boolean pingOnce(String ip) {
        try {
            Process p = Runtime.getRuntime().exec("/system/bin/ping -c 1 -W 1 " + ip);
            return p.waitFor() == 0;
        } catch (Exception e) {
            try {
                return InetAddress.getByName(ip).isReachable(600);
            } catch (Exception e2) {
                return false;
            }
        }
    }

    Set<String> dnsJson(String url) throws Exception {
        Set<String> out = new LinkedHashSet<String>();
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "application/dns-json");
        InputStream in = c.getInputStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        c.disconnect();
        org.json.JSONObject o = new org.json.JSONObject(sb.toString());
        org.json.JSONArray ans = o.optJSONArray("Answer");
        if (ans != null) {
            for (int i = 0; i < ans.length(); i++) {
                org.json.JSONObject a = ans.getJSONObject(i);
                out.add(a.optString("data") + "  (TTL " + a.optInt("TTL") + "s)");
            }
        }
        return out;
    }

    String proxyCheck() throws Exception {
        String px = val(0);
        String testUrl = val(1).length() == 0 ? "https://api.ipify.org" : val(1);
        if (!px.contains(":")) throw new Exception("Proxy must be host:port");
        String host = px.substring(0, px.indexOf(':')).trim();
        int port = Integer.parseInt(px.substring(px.indexOf(':') + 1).trim());
        long t0 = System.currentTimeMillis();
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        HttpURLConnection c = (HttpURLConnection) new URL(testUrl).openConnection(proxy);
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        int code = c.getResponseCode();
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        String body = r.readLine();
        r.close();
        c.disconnect();
        long ms = System.currentTimeMillis() - t0;
        return "PROXY CHECK: " + host + ":" + port + "\n\n"
                + "STATUS:  " + (code >= 200 && code < 400 ? "✓ WORKING" : "⚠ HTTP " + code) + "\n"
                + "LATENCY: " + ms + " ms\n"
                + "TEST URL RESPONSE:\n" + (body == null ? "" : body)
                + "\n\n(If the response IP differs from your own IP, the proxy is routing traffic.)";
    }

    String traceRoute() throws Exception {
        String host = val(0);
        if (host.length() == 0) throw new Exception("Enter a host");
        StringBuilder sb = new StringBuilder("TRACEROUTE to " + host + " (max 12 hops)\n\n");
        boolean reached = false;
        for (int ttl = 1; ttl <= 12 && !reached; ttl++) {
            Process p = Runtime.getRuntime().exec("/system/bin/ping -c 1 -t " + ttl + " -W 2 " + host);
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) out.append(line).append('\n');
            p.waitFor();
            r.close();
            String s = out.toString();
            String hop = null;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:From )?(\\d+\\.\\d+\\.\\d+\\.\\d+).*?(?:icmp_seq=\\d+ )?(Time to live exceeded|ttl=\\d+)").matcher(s);
            if (m.find()) hop = m.group(1);
            if (s.contains("ttl=") && !s.contains("Time to live exceeded")) {
                // final reply
                if (hop == null) hop = host;
                sb.append(String.format(Locale.US, "%2d  %-16s  destination reached ✓", ttl, hop)).append('\n');
                reached = true;
            } else if (hop != null) {
                sb.append(String.format(Locale.US, "%2d  %-16s  ttl exceeded (hop router)", ttl, hop)).append('\n');
            } else {
                sb.append(String.format(Locale.US, "%2d  %-16s  no reply", ttl, "*")).append('\n');
            }
        }
        if (!reached) sb.append("\nHost not reached within 12 hops (a firewall may block pings).");
        return sb.toString().trim();
    }

    String uptime() throws Exception {
        String u = val(0);
        if (!u.startsWith("http")) u = "https://" + u;
        int checks = 3;
        try {
            checks = Integer.parseInt(val(1));
        } catch (Exception ignore) {
        }
        if (checks < 1) checks = 1;
        if (checks > 10) checks = 10;
        int ok = 0;
        long totalMs = 0;
        StringBuilder sb = new StringBuilder("UPTIME MONITOR: " + u + "\n\n");
        for (int i = 1; i <= checks; i++) {
            long t0 = System.currentTimeMillis();
            int code;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestMethod("GET");
                code = c.getResponseCode();
                c.disconnect();
            } catch (Exception e) {
                code = -1;
            }
            long ms = System.currentTimeMillis() - t0;
            totalMs += ms;
            boolean good = code >= 200 && code < 400;
            if (good) ok++;
            sb.append(String.format(Locale.US, "#%d  %s  %d ms", i,
                    good ? "● UP (" + code + ")" : "✗ DOWN" + (code > 0 ? " (" + code + ")" : ""), ms)).append('\n');
            if (i < checks) Thread.sleep(700);
        }
        sb.append("\nUPTIME:  ").append(ok * 100 / checks).append("%  (").append(ok).append('/').append(checks).append(" checks OK)")
                .append("\nAVG LATENCY: ").append(totalMs / checks).append(" ms");
        return sb.toString().trim();
    }

    String wifiInfo() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
            String ssid = wi.getSSID();
            if (ssid != null) ssid = ssid.replace("\"", "");
            int rssi = wi.getRssi();
            int freq = wi.getFrequency();
            int channel;
            if (freq >= 2412 && freq <= 2484) channel = (freq - 2412) / 5 + 1;
            else if (freq >= 5000 && freq <= 5900) channel = (freq - 5000) / 5;
            else channel = 0;
            int ipInt = wi.getIpAddress();
            String ip = (ipInt & 255) + "." + ((ipInt >> 8) & 255) + "."
                    + ((ipInt >> 16) & 255) + "." + ((ipInt >> 24) & 255);
            StringBuilder sb = new StringBuilder("WIFI CONNECTION\n\n");
            sb.append("SSID:       ").append(ssid == null || ssid.length() == 0 ? "(not connected)" : ssid).append('\n')
                    .append("BSSID:      ").append(wi.getBSSID()).append('\n')
                    .append("SIGNAL:     ").append(rssi).append(" dBm  (")
                    .append(rssi > -55 ? "excellent" : rssi > -67 ? "good" : rssi > -80 ? "fair" : "weak")
                    .append(")\n")
                    .append("LINK SPEED: ").append(wi.getLinkSpeed()).append(" Mbps\n")
                    .append("FREQUENCY:  ").append(freq).append(" MHz\n")
                    .append("CHANNEL:     ").append(channel > 0 ? String.valueOf(channel) : "?").append('\n')
                    .append("YOUR IP:    ").append(ip).append('\n');
            sb.append("\nWIFI SECURITY: ")
                    .append(android.text.TextUtils.isEmpty(wi.toString()) ? "?" :
                            wi.toString().contains("WPA3") ? "WPA3" : "WPA/WPA2 (see settings)")
                    .append('\n');
            return sb.toString().trim();
        } catch (Exception e) {
            return "WiFi info unavailable: " + e.getMessage();
        }
    }

    // ------------------------------------------------------------- helpers

    String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "GIToolbox/3.0");
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        c.disconnect();
        return sb.toString();
    }

    String whois(String server, String query) throws Exception {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(server, 43), 10000);
        s.setSoTimeout(10000);
        OutputStream os = s.getOutputStream();
        os.write((query + "\r\n").getBytes());
        os.flush();
        BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        s.close();
        return sb.toString();
    }

    String trim(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max) + "\n… (truncated)";
    }
}
