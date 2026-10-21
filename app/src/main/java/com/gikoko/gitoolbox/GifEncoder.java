package com.gikoko.gitoolbox;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Minimal GIF89a encoder with a fixed 256-color web palette.
 * Used by GIF Maker (ImageToolActivity) and Video → GIF (VideoToolActivity).
 */
public final class GifEncoder {

    private GifEncoder() {
    }

    // Fixed palette: 6x7x6 color cube + 2 grays ≈ close enough for photos.
    private static final int[] PALETTE = buildPalette();

    private static int[] buildPalette() {
        int[] p = new int[256];
        int i = 0;
        int[] r = {0, 51, 102, 153, 204, 255};
        int[] g = {0, 43, 85, 128, 170, 212, 255};
        int[] b = {0, 51, 102, 153, 204, 255};
        for (int gi = 0; gi < 7; gi++) {
            for (int ri = 0; ri < 6; ri++) {
                for (int bi = 0; bi < 6; bi++) {
                    p[i++] = 0xFF000000 | (r[ri] << 16) | (g[gi] << 8) | b[bi];
                }
            }
        }
        while (i < 256) {
            p[i++] = 0xFF000000 | (i * 255 / 255) << 16 | 0x80;
        }
        return p;
    }

    /** Quantize an ARGB pixel to the nearest palette index. */
    private static int quantize(int pixel) {
        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;
        int ri = Math.round(r * 5f / 255f);
        int gi = Math.round(g * 6f / 255f);
        int bi = Math.round(b * 5f / 255f);
        // index = gi*36 + ri*6 + bi
        int idx = (gi * 36 + ri * 6 + bi) & 0xFF;
        return idx;
    }

    /**
     * Encode frames (same size) into an animated GIF written to out.
     * delayCs = delay per frame in centiseconds (e.g. 50 = 0.5s).
     */
    public static void encode(List<Bitmap> frames, int delayCs, OutputStream out) throws IOException {
        if (frames == null || frames.size() == 0) throw new IOException("No frames");
        Bitmap f0 = frames.get(0);
        int w = f0.getWidth();
        int h = f0.getHeight();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // header
        writeAscii(buf, "GIF89a");
        // logical screen descriptor
        writeShort(buf, w);
        writeShort(buf, h);
        buf.write(0xF7); // global color table, 8 bit, 256 entries, size 7
        buf.write(0);    // bg color index
        buf.write(0);    // aspect
        // global color table (256 * 3)
        for (int i = 0; i < 256; i++) {
            int c = PALETTE[i];
            buf.write((c >> 16) & 0xFF);
            buf.write((c >> 8) & 0xFF);
            buf.write(c & 0xFF);
        }
        // netscape loop extension (infinite)
        buf.write(0x21);
        buf.write(0xFF);
        buf.write(0x0B);
        writeAscii(buf, "NETSCAPE2.0");
        buf.write(3);
        buf.write(1);
        writeShort(buf, 0); // loop forever
        buf.write(0);

        for (int i = 0; i < frames.size(); i++) {
            Bitmap f = frames.get(i);
            Bitmap frame = f;
            if (f.getWidth() != w || f.getHeight() != h) {
                frame = Bitmap.createScaledBitmap(f, w, h, true);
            }
            int[] px = new int[w * h];
            frame.getPixels(px, 0, w, 0, 0, w, h);
            byte[] idx = new byte[w * h];
            for (int p = 0; p < px.length; p++) idx[p] = (byte) quantize(px[p]);

            // graphic control extension
            buf.write(0x21);
            buf.write(0xF9);
            buf.write(4);
            buf.write(0x04); // disposal = 1 (do not dispose) | no transparency... use 0x04
            writeShort(buf, Math.max(2, delayCs));
            buf.write(0); // transparent index none
            buf.write(0); // block terminator

            // image descriptor
            buf.write(0x2C);
            writeShort(buf, 0);
            writeShort(buf, 0);
            writeShort(buf, w);
            writeShort(buf, h);
            buf.write(0); // no local color table

            lzwEncode(idx, w, buf);

            if (frame != f) frame.recycle();
        }
        buf.write(0x3B); // trailer
        out.write(buf.toByteArray());
        out.flush();
    }

    /** Encode one still frame to GIF bytes (used for previews). */
    public static byte[] encodeStill(Bitmap b) throws IOException {
        java.util.ArrayList<Bitmap> one = new java.util.ArrayList<Bitmap>();
        one.add(b);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        encode(one, 10, os);
        return os.toByteArray();
    }

    // ------------------------------------------------------------- LZW

    private static void lzwEncode(byte[] pixels, int minWidth, OutputStream out) throws IOException {
        int clearCode = 256;
        int endCode = 257;
        int codeSize = 9;
        int nextCode = 258;
        int dictSize = 512;

        // string table: children[code] maps (code, pixel) -> code. use int maps
        java.util.HashMap<Long, Integer> dict = new java.util.HashMap<Long, Integer>();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BitWriter bits = new BitWriter(bytes);
        bits.write(clearCode, codeSize);

        int cur = pixels[0] & 0xFF;
        for (int i = 1; i < pixels.length; i++) {
            int k = pixels[i] & 0xFF;
            Long key = ((long) cur << 8) | k;
            Integer found = dict.get(key);
            if (found != null) {
                cur = found;
            } else {
                bits.write(cur, codeSize);
                if (nextCode < 4096) {
                    dict.put(key, nextCode++);
                    if (nextCode == dictSize && codeSize < 12) {
                        codeSize++;
                        dictSize <<= 1;
                    }
                } else {
                    bits.write(clearCode, codeSize);
                    dict.clear();
                    codeSize = 9;
                    dictSize = 512;
                    nextCode = 258;
                }
                cur = k;
            }
        }
        bits.write(cur, codeSize);
        bits.write(endCode, codeSize);
        bits.flush();

        // data sub-blocks
        byte[] all = bytes.toByteArray();
        bufWriteBlocks(out, all);
    }

    private static void bufWriteBlocks(OutputStream out, byte[] data) throws IOException {
        int pos = 0;
        while (pos < data.length) {
            int len = Math.min(255, data.length - pos);
            out.write(len);
            out.write(data, pos, len);
            pos += len;
        }
        out.write(0); // terminator
    }

    private static void writeAscii(OutputStream os, String s) throws IOException {
        os.write(s.getBytes("US-ASCII"));
    }

    private static void writeShort(OutputStream os, int v) throws IOException {
        os.write(v & 0xFF);
        os.write((v >> 8) & 0xFF);
    }

    private static final class BitWriter {
        private final OutputStream out;
        private int cur = 0;
        private int n = 0;

        BitWriter(OutputStream out) {
            this.out = out;
        }

        void write(int code, int size) throws IOException {
            for (int i = 0; i < size; i++) {
                int bit = (code >> i) & 1;
                cur |= bit << n;
                n++;
                if (n == 8) {
                    out.write(cur);
                    cur = 0;
                    n = 0;
                }
            }
        }

        void flush() throws IOException {
            if (n > 0) out.write(cur);
            cur = 0;
            n = 0;
        }
    }

    /** Handy: render an emoji/label onto a frame (not used by default). */
    static Bitmap labeled(Bitmap src, String label) {
        Bitmap b = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextSize(Math.max(14f, b.getWidth() / 18f));
        c.drawText(label, 8, p.getTextSize(), p);
        return b;
    }
}
