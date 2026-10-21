package com.gikoko.gitoolbox;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** GPS Coordinates / GPS Speed / Speedometer. */
public class GpsActivity extends Activity implements LocationListener {

    private String tool;
    private LocationManager lm;
    private TextView value, extra;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tool = getIntent().getStringExtra("tool");
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        LinearLayout root = Ui.screen(this);
        root.addView(Ui.header(this, tool, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));

        LinearLayout card = Ui.card(this);
        value = Ui.text(this, "Waiting for GPS…", 16, Ui.GREEN, true);
        value.setTypeface(android.graphics.Typeface.MONOSPACE);
        card.addView(value);
        extra = Ui.text(this, "", 12, Ui.DIM, false);
        card.addView(extra);
        root.addView(card);

        Button req = Ui.button(this, "📍 GET LOCATION");
        req.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestLocation();
            }
        });
        root.addView(req);

        setContentView(Ui.scroll(this, root));
        requestLocation();
    }

    void requestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            value.setText("Location permission needed.\nTap GET LOCATION again after allowing.");
            return;
        }
        boolean net = false, gps = false;
        try {
            net = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {
        }
        if (!gps && !net) {
            value.setText("Location is OFF.\nEnable location in phone settings, then tap GET LOCATION.");
            return;
        }
        try {
            if (gps) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
                Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (last != null) onLocationChanged(last);
            }
            if (net) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 0, this);
                Location last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (last != null && !gps) onLocationChanged(last);
            }
        } catch (SecurityException se) {
            value.setText("Permission error: " + se.getMessage());
        }
        extra.setText("Provider: " + (gps ? "GPS" : "Network") + (gps && net ? " + Network" : "")
                + "\nUpdates every second while this screen is open.");
    }

    @Override
    public void onLocationChanged(Location loc) {
        if (tool.equals("GPS Speed") || tool.equals("Speedometer")) {
            double mps = loc.getSpeed();
            double kmh = mps * 3.6;
            value.setText("⚡ " + Ui.fmt(kmh, 1) + " km/h"
                    + "\n\n" + Ui.fmt(mps, 1) + " m/s"
                    + "\n" + Ui.fmt(kmh * 0.621371, 1) + " mph"
                    + "\nAccuracy: " + Ui.fmt(loc.getAccuracy(), 0) + " m");
            extra.setText("Speed comes from GPS — works best outdoors / on the move.");
        } else {
            value.setText("LAT:  " + Ui.fmt(loc.getLatitude(), 6)
                    + "\nLON:  " + Ui.fmt(loc.getLongitude(), 6)
                    + "\n\nALT:  " + Ui.fmt(loc.getAltitude(), 0) + " m"
                    + "\nACC:  ±" + Ui.fmt(loc.getAccuracy(), 0) + " m");
            extra.setText("Google Maps link:\nhttps://maps.google.com/?q="
                    + loc.getLatitude() + "," + loc.getLongitude());
        }
    }

    @Override
    public void onStatusChanged(String p, int s, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String p) {
    }

    @Override
    public void onProviderDisabled(String p) {
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            lm.removeUpdates(this);
        } catch (Exception ignored) {
        }
    }
}
