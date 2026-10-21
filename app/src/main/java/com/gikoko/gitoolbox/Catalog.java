package com.gikoko.gitoolbox;

/** The 14 x 25 = 350 tool catalog. */
public final class Catalog {

    public static final String[][] CATS = {
            {"Image Tools", "\uD83D\uDDBC", "Image Compressor", "Image Resizer", "PNG → JPG", "JPG → PNG", "WebP Converter", "Image Cropper", "Rotate Image", "Flip Image", "Base64 Encode", "Base64 Decode", "Image → PDF", "Photo Watermark", "Image Blur", "Sharpen Image", "Grayscale Filter", "Brightness Adjust", "Contrast Adjust", "Color Picker", "Screenshot Tool", "Palette Generator", "ICO Converter", "GIF Maker", "Meme Maker", "Image Splitter", "Image Merger"},
            {"PDF Tools", "\uD83D\uDCC4", "PDF Merger", "PDF Splitter", "PDF Compressor", "PDF → Images", "Images → PDF", "Delete PDF Pages", "Rotate PDF Pages", "Extract PDF Text", "PDF Watermark", "PDF Password Lock", "PDF Password Unlock", "PDF Metadata Editor", "Reorder Pages", "HTML → PDF", "Text → PDF", "Markdown → PDF", "Excel → PDF", "Word → PDF", "PDF → Word", "PDF → Text", "PDF Viewer", "PDF Annotation", "PDF Bookmarks", "Crop PDF Pages", "PDF Flattener"},
            {"Text & Code", "\uD83D\uDCDD", "Word Counter", "Character Counter", "Case Converter", "Reverse Text", "Remove Duplicates", "Remove Line Breaks", "Text Sorter", "Text Diff", "Lorem Ipsum", "Text to Slug", "Find & Replace", "Text Formatter", "JSON Formatter", "JSON → XML", "XML → JSON", "JSON Validator", "CSV → JSON", "JSON → CSV", "SQL Formatter", "HTML Formatter", "CSS Formatter", "JS Minifier", "CSS Minifier", "Markdown Preview", "Text Encrypter"},
            {"Converters", "\uD83D\uDD04", "Unit Converter", "Length Converter", "Weight Converter", "Temperature", "Speed Converter", "Area Converter", "Volume Converter", "Data Size", "Time Converter", "Angle Converter", "Pressure Converter", "Energy Converter", "Power Converter", "Fuel Economy", "Shoe Size", "Clothing Size", "Roman Numerals", "Number Base", "Timestamp Converter", "Timezone Converter", "Currency Rates", "Text → Binary", "Binary → Text", "Text → Hex", "Morse Code"},
            {"Calculators", "\uD83E\uDDEE", "Basic Calculator", "Scientific Calculator", "Age Calculator", "BMI Calculator", "Loan / EMI", "Compound Interest", "Discount Calculator", "Percentage Calc", "GST / VAT", "Tip Calculator", "Date Difference", "Date Add / Sub", "Time Calculator", "Fuel Cost", "Salary Calculator", "Income Tax", "Bill Split", "Ratio Calculator", "Fraction Calc", "Random Number", "Matrix Calculator", "Quadratic Solver", "Statistics Calc", "Prime Checker", "Number Series"},
            {"Network & IP", "\uD83C\uDF10", "IP Lookup", "My IP Address", "Ping Tool", "Port Scanner", "DNS Lookup", "WHOIS Lookup", "Subnet Calculator", "MAC Lookup", "Speed Test", "WiFi Analyzer", "HTTP Headers", "URL Encoder", "URL Decoder", "SSL Checker", "Trace Route", "Wake on LAN", "Geo IP Map", "Email Validator", "MX Record Lookup", "Bandwidth Calc", "Uptime Monitor", "Proxy Checker", "HTTP Status Codes", "IP Range Scanner", "DNS Propagation"},
            {"QR & Barcode", "\uD83D\uDD33", "QR Generator", "QR Scanner", "QR with Logo", "WiFi QR", "vCard QR", "Email QR", "SMS QR", "Phone QR", "Event QR", "Geo QR", "Text QR", "URL QR", "Batch QR", "QR History", "Barcode Generator", "Barcode Scanner", "EAN-13", "Code 128", "UPC-A", "PDF417", "Aztec Code", "Data Matrix", "Code 39", "Codabar", "ITF-14"},
            {"Device & Sensor", "\uD83D\uDCF1", "Device Info", "CPU Monitor", "RAM Monitor", "Storage Info", "Battery Status", "Battery Health", "Screen Test", "Dead Pixel Test", "Touch Test", "Sensor List", "Accelerometer", "Gyroscope", "Compass", "Magnetometer", "Light Sensor", "Proximity Sensor", "GPS Coordinates", "GPS Speed", "Altimeter", "Sound Meter", "Vibration Test", "Flashlight", "Brightness Test", "Multi-Touch Test", "Speaker Test"},
            {"Security & Privacy", "\uD83D\uDD10", "Password Generator", "PIN Generator", "Passphrase Generator", "Password Strength", "MD5 Hash", "SHA-1 Hash", "SHA-256 Hash", "SHA-512 Hash", "HMAC Generator", "Checksum Tool", "CRC32 Hash", "Base32 Encode", "AES Encrypt", "AES Decrypt", "TOTP Generator", "Credit Card Validator", "IBAN Validator", "File Checksum", "Secure Notes", "UUID Generator", "Random Bytes", "Crypto Key Generator", "Text Obfuscator", "Privacy Cleaner", "App Lock"},
            {"Audio & Video", "\uD83C\uDFB5", "Audio Recorder", "Audio Cutter", "Audio Converter", "Audio Merger", "Volume Booster", "Speed Changer", "Noise Reducer", "Audio Extractor", "Video Compressor", "Video Cutter", "Video Merger", "Video → GIF", "Video Speed", "Video Reverse", "Video Mute", "Video Rotate", "Video Crop", "Frame Extractor", "MP3 Cutter", "WAV → MP3", "MP3 → WAV", "M4A Converter", "FLAC Converter", "Voice Changer", "Metronome"},
            {"Utility & Daily", "\uD83D\uDEE0", "Flashlight Torch", "Mirror Camera", "Ruler", "Protractor", "Bubble Level", "Speedometer", "Clinometer", "Alarm Clock", "Stopwatch", "Timer", "Pomodoro Timer", "World Clock", "Countdown", "Calendar", "Habit Tracker", "Quick Notes", "To-Do List", "Reminder", "Dice Roller", "Coin Flip", "Random Picker", "Decision Wheel", "Countdown Days", "Name Picker", "Price Compare"},
            {"AI Tools", "\uD83E\uDD16", "Text Summarizer", "Text Translator", "Grammar Fixer", "Paraphraser", "AI Writer", "Code Explainer", "Code Generator", "Regex Explainer", "Regex Builder", "Text to Emoji", "Tone Analyzer", "Keyword Extractor", "Name Generator", "Bio Generator", "Email Writer", "Poem Writer", "Story Writer", "Quote Maker", "Slogan Maker", "Resume Builder", "Blog Outline", "Product Description", "Study Notes Maker", "Flashcard Maker", "Idea Brainstorm"},
            {"System Pro", "\uD83D\uDD27", "App Manager", "APK Extractor", "App Uninstaller", "App Sizes", "Exported Checker", "Task Manager", "RAM Boost", "Cache Cleaner", "Permission Manager", "Startup Manager", "Data Usage", "Network Monitor", "Clipboard Manager", "Keep Screen On", "Volume Manager", "Sound Profile", "Ringtone Picker", "Refresh Rate", "WebView Version", "Keyboard List", "TTS Engines", "Accessibility Checker", "Uptime", "Kernel Info", "Storage Volumes"},
            {"WiFi & Comm", "\uD83D\uDCF6", "WiFi Call", "Walkie-Talkie", "Phone Control (LAN)", "Find My Phone", "CCTV Camera", "LAN Chat", "LAN Clipboard Sync", "Battery Remote", "Sound Generator", "Tone Generator", "Ultrasonic Generator", "Hearing Test", "Morse Flasher", "Flashlight SOS", "Alarm Siren", "White Noise", "Decibel Alarm", "Text to Speech", "Stop Motion Studio", "Myanmar Calendar", "Myanmar Numerals", "Age (Myanmar)", "Bluetooth Checker", "NFC Checker", "WiFi Signal Meter"},
    };

    private Catalog() {
    }

    public static String[] cat(int i) {
        return CATS[i];
    }

    public static String tool(int cat, int idx) {
        return CATS[cat][idx + 2];
    }

    public static int[] find(String name) {
        for (int c = 0; c < CATS.length; c++) {
            for (int t = 0; t < 25; t++) {
                if (CATS[c][t + 2].equals(name)) return new int[]{c, t};
            }
        }
        return null;
    }
}
