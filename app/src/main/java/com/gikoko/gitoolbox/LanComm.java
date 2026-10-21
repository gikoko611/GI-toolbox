package com.gikoko.gitoolbox;

import android.content.Context;
import android.os.BatteryManager;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/** Small LAN helpers shared by the WiFi & Comm suite. */
public final class LanComm {

    public static final int PORT = 8899;

    private LanComm() {
    }

    /** Site-local IPv4 of this phone (Wi-Fi hotspot client or LAN), or "?". */
    public static String localIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!a.isLoopbackAddress() && a.isSiteLocalAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "?";
    }

    public static String batteryLine(Context c) {
        try {
            BatteryManager bm = (BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
            int cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            return "BATT:" + (cap == Integer.MIN_VALUE ? "?" : cap) + ":" + (bm.isCharging() ? 1 : 0);
        } catch (Exception e) {
            return "BATT:?:0";
        }
    }
}
