package com.gikoko.gitoolbox;

import android.content.Context;
import android.content.Intent;

/** Routes a tool name to the right native activity. */
public final class Router {

    private Router() {
    }

    public static void open(Context ctx, String tool) {
        Class<?> cls = special(tool);
        Intent it;
        if (cls != null) {
            it = new Intent(ctx, cls);
        } else if (Compute.has(tool)) {
            it = new Intent(ctx, ToolActivity.class);
        } else {
            it = new Intent(ctx, PanelActivity.class);
        }
        it.putExtra("tool", tool);
        ctx.startActivity(it);
    }

    private static Class<?> special(String t) {
        // Device & Sensor
        if (t.equals("Device Info") || t.equals("Storage Info") || t.equals("Screen Test") || t.equals("Dead Pixel Test"))
            return InfoActivity.class;
        if (t.equals("CPU Monitor") || t.equals("RAM Monitor") || t.equals("Battery Status") || t.equals("Battery Health"))
            return InfoActivity.class;
        if (t.equals("Sensor List") || t.equals("Accelerometer") || t.equals("Gyroscope") || t.equals("Compass")
                || t.equals("Magnetometer") || t.equals("Light Sensor") || t.equals("Proximity Sensor") || t.equals("Altimeter"))
            return SensorActivity.class;
        if (t.equals("GPS Coordinates") || t.equals("GPS Speed") || t.equals("Speedometer"))
            return GpsActivity.class;
        if (t.equals("Sound Meter")) return SoundMeterActivity.class;
        if (t.equals("Vibration Test")) return HardwareActivity.class;
        if (t.equals("Flashlight") || t.equals("Flashlight Torch")) return HardwareActivity.class;
        if (t.equals("Mirror Camera")) return HardwareActivity.class;
        if (t.equals("Touch Test") || t.equals("Multi-Touch Test") || t.equals("Brightness Test") || t.equals("Speaker Test"))
            return ScreenTestActivity.class;
        // Utility
        if (t.equals("Ruler") || t.equals("Protractor") || t.equals("Bubble Level") || t.equals("Clinometer"))
            return MeasureActivity.class;
        if (t.equals("Stopwatch") || t.equals("Timer") || t.equals("Pomodoro Timer") || t.equals("World Clock")
                || t.equals("Countdown") || t.equals("Alarm Clock"))
            return ClockActivity.class;
        if (t.equals("Quick Notes") || t.equals("To-Do List") || t.equals("Habit Tracker") || t.equals("Reminder")
                || t.equals("Secure Notes") || t.equals("QR History"))
            return ListToolActivity.class;
        // QR & Barcode
        if (t.startsWith("QR ") || t.equals("Barcode Generator") || t.equals("EAN-13") || t.equals("Code 39")
                || t.equals("Codabar") || t.equals("ITF-14") || t.equals("UPC-A"))
            return QrActivity.class;
        // Network
        if (t.equals("My IP Address") || t.equals("IP Lookup") || t.equals("Geo IP Map") || t.equals("DNS Lookup")
                || t.equals("Speed Test") || t.equals("Ping Tool") || t.equals("HTTP Headers") || t.equals("SSL Checker")
                || t.equals("WHOIS Lookup") || t.equals("Wake on LAN") || t.equals("Currency Rates"))
            return NetActivity.class;
        // Image
        if (t.equals("Base64 Decode")) return ImageToolActivity.class;
        if (isImage(t)) return ImageToolActivity.class;
        // PDF
        if (t.equals("Text → PDF") || t.equals("Markdown → PDF") || t.equals("Images → PDF") || t.equals("Image → PDF")
                || t.equals("PDF → Images") || t.equals("PDF Viewer") || t.equals("PDF Metadata Editor"))
            return PdfToolActivity.class;
        // Audio
        if (t.equals("Audio Recorder") || t.equals("Metronome")) return AudioToolActivity.class;
        return null;
    }

    private static boolean isImage(String t) {
        return t.equals("Image Compressor") || t.equals("Image Resizer") || t.equals("PNG → JPG") || t.equals("JPG → PNG")
                || t.equals("WebP Converter") || t.equals("Image Cropper") || t.equals("Rotate Image") || t.equals("Flip Image")
                || t.equals("Base64 Encode") || t.equals("Photo Watermark") || t.equals("Grayscale Filter")
                || t.equals("Brightness Adjust") || t.equals("Contrast Adjust") || t.equals("Color Picker")
                || t.equals("Palette Generator") || t.equals("Meme Maker") || t.equals("Image Splitter") || t.equals("Image Merger");
    }
}
