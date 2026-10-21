package com.gikoko.gitoolbox;

import android.app.Activity;
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
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;

/** Network & IP tools — all requests run on background threads. */
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
        return new String[][]{}; // My IP Address needs no input
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
            StringBuilder sb = new StringBuilder();
            sb.append("IP:        ").append(o.optString("ip")).append('\n')
                    .append("TYPE:      ").append(o.optString("type")).append('\n')
                    .append("COUNTRY:   ").append(o.optString("country")).append(" (").append(o.optString("country_code")).append(")\n")
                    .append("REGION:    ").append(o.optString("region")).append('\n')
                    .append("CITY:      ").append(o.optString("city")).append('\n')
                    .append("LAT/LON:   ").append(lat).append(", ").append(lon).append('\n')
                    .append("ISP:       ").append(o.optString("connection", ""));
            org.json.JSONObject conn = o.optJSONObject("connection");
            if (conn != null) {
                sb = new StringBuilder(sb.toString().replace(o.optString("connection"), conn.optString("isp", "")));
                sb.append("\nORG:       ").append(conn.optString("org", ""));
            }
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
            // try native ping first (Redmi supports it)
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
            // follow referral
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)whois:\\s*(\\S+)").matcher(raw);
            if (m.find()) {
                String server = m.group(1);
                String real = whois(server, domain);
                if (real.contains("No match") || real.trim().length() == 0) return raw;
                return "Server: " + server + "\n\n" + trim(raw.length() > 200 ? real : real, 2500);
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

    String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "GIToolbox/2.0");
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
        java.net.Socket s = new java.net.Socket();
        s.connect(new java.net.InetSocketAddress(server, 43), 10000);
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
