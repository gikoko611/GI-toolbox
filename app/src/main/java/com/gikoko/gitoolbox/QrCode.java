package com.gikoko.gitoolbox;

import java.util.ArrayList;
import java.util.List;

/**
 * Byte-mode QR encoder, versions 1-6, ECC level M, full Reed-Solomon EC,
 * proper interleaving, function-module reservation and mask selection.
 * Matrix layout follows ISO/IEC 18004 (same placement as reference encoders).
 */
final class QrCode {

    private QrCode() {
    }

    // ---- GF(256), generator poly 0x11D ----
    static final int[] EXP = new int[512];
    static final int[] LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) x ^= 0x11D;
        }
        for (int i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
    }

    static int mul(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return EXP[LOG[a] + LOG[b]];
    }

    static int[] rsGen(int ecLen) {
        int[] poly = {1};
        for (int i = 0; i < ecLen; i++) {
            int[] np = new int[poly.length + 1];
            for (int j = 0; j < poly.length; j++) {
                np[j] ^= poly[j];                       // multiply by x
                np[j + 1] ^= mul(poly[j], EXP[i]);      // by alpha^i
            }
            poly = np;
        }
        return poly; // coefficient length ecLen+1, [0] = 1
    }

    static int[] rsEncode(int[] data, int ecLen) {
        int[] gen = rsGen(ecLen);
        int[] res = new int[ecLen];
        for (int d : data) {
            int factor = d ^ res[0];
            System.arraycopy(res, 1, res, 0, ecLen - 1);
            res[ecLen - 1] = 0;
            for (int i = 0; i < ecLen; i++) {
                res[i] ^= mul(gen[i + 1], factor);
            }
        }
        return res;
    }

    // ---- version tables (ECC M, byte mode) v1..v6 ----
    static final int[] DATA_CW = {16, 28, 44, 64, 86, 108};
    static final int[] BLOCKS = {1, 1, 1, 2, 2, 4};
    static final int[] EC_CW = {10, 16, 26, 18, 24, 16};
    static final int[] TOTAL_CW = {26, 44, 70, 100, 134, 172};
    static final int[][] ALIGN = {{}, {6, 18}, {6, 22}, {6, 26}, {6, 30}, {6, 34}};
    static final int[] CAP = {14, 26, 42, 62, 84, 106};

    public static int capacity() {
        return CAP[CAP.length - 1];
    }

    /** Encode text to a QR matrix (m[row][col], true = dark). */
    public static boolean[][] encode(String text) throws Exception {
        byte[] bytes = text.getBytes("ISO-8859-1");
        if (bytes.length > CAP[CAP.length - 1]) {
            throw new Exception("Too long for QR (max " + CAP[CAP.length - 1] + " bytes)");
        }
        int ver = 0;
        while (ver < CAP.length && bytes.length > CAP[ver]) ver++;

        // bit stream
        int dataCw = DATA_CW[ver];
        List<Integer> bits = new ArrayList<>();
        pushBits(bits, 4, 4);                 // byte mode
        pushBits(bits, bytes.length, 8);      // v1-6 -> 8-bit count
        for (byte b : bytes) pushBits(bits, b & 0xFF, 8);
        int capacityBits = dataCw * 8;
        for (int i = 0; i < Math.min(4, capacityBits - bits.size()); i++) bits.add(0);
        while (bits.size() % 8 != 0) bits.add(0);
        int[] dataCodewords = new int[dataCw];
        for (int i = 0; i < dataCw; i++) {
            int v = 0;
            for (int j = 0; j < 8; j++) {
                int idx = i * 8 + j;
                v = (v << 1) | (idx < bits.size() && bits.get(idx) == 1 ? 1 : 0);
            }
            dataCodewords[i] = v;
        }
        int[] padSeq = {0xEC, 0x11};
        int pi = 0;
        for (int i = bits.size() / 8; i < dataCw; i++) {
            dataCodewords[i] = padSeq[pi];
            pi ^= 1;
        }

        // RS blocks
        int nBlocks = BLOCKS[ver];
        int per = dataCw / nBlocks;
        int[][] dataBlocks = new int[nBlocks][per];
        int[][] ecBlocks = new int[nBlocks][];
        for (int i = 0; i < nBlocks; i++) {
            System.arraycopy(dataCodewords, i * per, dataBlocks[i], 0, per);
            ecBlocks[i] = rsEncode(dataBlocks[i], EC_CW[ver]);
        }
        // interleave
        int[] all = new int[TOTAL_CW[ver]];
        int pos = 0;
        for (int i = 0; i < per; i++) for (int b = 0; b < nBlocks; b++) all[pos++] = dataBlocks[b][i];
        for (int i = 0; i < EC_CW[ver]; i++) for (int b = 0; b < nBlocks; b++) all[pos++] = ecBlocks[b][i];

        // pick best mask
        boolean[][] best = null;
        long bestPenalty = Long.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            boolean[][] m = buildMatrix(ver, all, mask);
            long p = penalty(m);
            if (p < bestPenalty) {
                bestPenalty = p;
                best = m;
            }
        }
        return best;
    }

    static void pushBits(List<Integer> bits, int value, int n) {
        for (int i = n - 1; i >= 0; i--) bits.add((value >> i) & 1);
    }

    static boolean[][] buildMatrix(int ver, int[] codewords, int mask) {
        int size = 17 + 4 * (ver + 1);
        boolean[][] m = new boolean[size][size];
        boolean[][] func = new boolean[size][size];

        // finder + separators
        drawFinder(m, func, 0, 0);
        drawFinder(m, func, size - 7, 0);
        drawFinder(m, func, 0, size - 7);
        // timing
        for (int i = 8; i < size - 8; i++) {
            boolean on = i % 2 == 0;
            m[6][i] = on;
            func[6][i] = true;
            m[i][6] = on;
            func[i][6] = true;
        }
        // alignment (skip the three finder corners)
        int[] ap = ALIGN[ver];
        for (int r : ap) {
            for (int c : ap) {
                boolean corner = (r == 6 && c == 6) || (r == 6 && c == size - 7) || (r == size - 7 && c == 6);
                if (!corner) drawAlignment(m, func, c, r);
            }
        }
        // format reservation areas
        for (int i = 0; i <= 8; i++) {
            func[i][8] = true;
            func[8][i] = true;
        }
        for (int i = 0; i < 8; i++) {
            func[8][size - 1 - i] = true;
            func[size - 1 - i][8] = true;
        }
        // dark module
        m[size - 8][8] = true;
        func[size - 8][8] = true;

        // data placement
        int totalBits = codewords.length * 8;
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!func[y][x] && i < totalBits) {
                        boolean bit = ((codewords[i >>> 3] >> (7 - (i & 7))) & 1) == 1;
                        m[y][x] = applyMask(mask, y, x) != bit;
                        i++;
                    }
                }
            }
        }

        drawFormat(m, size, mask);
        return m;
    }

    static void drawFinder(boolean[][] m, boolean[][] func, int col, int row) {
        for (int r = -1; r < 8; r++) {
            for (int c = -1; c < 8; c++) {
                int rr = row + r, cc = col + c;
                if (rr < 0 || cc < 0 || rr >= m.length || cc >= m.length) continue;
                boolean dark = (r >= 0 && r <= 6 && (c == 0 || c == 6))
                        || (c >= 0 && c <= 6 && (r == 0 || r == 6))
                        || (r >= 2 && r <= 4 && c >= 2 && c <= 4);
                m[rr][cc] = dark;
                func[rr][cc] = true;
            }
        }
    }

    static void drawAlignment(boolean[][] m, boolean[][] func, int col, int row) {
        for (int r = -2; r <= 2; r++) {
            for (int c = -2; c <= 2; c++) {
                boolean dark = Math.max(Math.abs(r), Math.abs(c)) != 1;
                m[row + r][col + c] = dark;
                func[row + r][col + c] = true;
            }
        }
    }

    static boolean applyMask(int mask, int r, int c) {
        switch (mask) {
            case 0: return (r + c) % 2 == 0;
            case 1: return r % 2 == 0;
            case 2: return c % 3 == 0;
            case 3: return (r + c) % 3 == 0;
            case 4: return ((r / 2) + (c / 3)) % 2 == 0;
            case 5: return (r * c) % 2 + (r * c) % 3 == 0;
            case 6: return ((r * c) % 2 + (r * c) % 3) % 2 == 0;
            default: return ((r + c) % 2 + (r * c) % 3) % 2 == 0;
        }
    }

    static void drawFormat(boolean[][] m, int size, int mask) {
        int data = (0 << 13) | (mask << 10); // ECC level M = 00
        int rem = data;
        for (int i = 0; i < 10; i++) {
            rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        }
        int bits = ((data << 10) | rem) ^ 0x5412;
        // first copy
        for (int i = 0; i <= 5; i++) m[i][8] = getBit(bits, i);
        m[7][8] = getBit(bits, 6);
        m[8][8] = getBit(bits, 7);
        m[8][7] = getBit(bits, 8);
        for (int j = 9; j < 15; j++) m[8][14 - j] = getBit(bits, j);
        // second copy
        for (int i = 0; i < 8; i++) m[8][size - 1 - i] = getBit(bits, i);
        for (int j = 8; j < 15; j++) m[size - 15 + j][8] = getBit(bits, j);
        // always-dark module
        m[size - 8][8] = true;
    }

    static boolean getBit(int v, int i) {
        return ((v >> i) & 1) == 1;
    }

    static long penalty(boolean[][] m) {
        long p = 0;
        int size = m.length;
        for (int r = 0; r < size; r++) {
            int run = 1;
            for (int c = 1; c < size; c++) {
                if (m[r][c] == m[r][c - 1]) {
                    run++;
                    if (run == 5) p += 3;
                    else if (run > 5) p += 1;
                } else run = 1;
            }
        }
        for (int c = 0; c < size; c++) {
            int run = 1;
            for (int r = 1; r < size; r++) {
                if (m[r][c] == m[r - 1][c]) {
                    run++;
                    if (run == 5) p += 3;
                    else if (run > 5) p += 1;
                } else run = 1;
            }
        }
        long dark = 0;
        for (int r = 0; r < size; r++) for (int c = 0; c < size; c++) if (m[r][c]) dark++;
        double ratio = dark * 100.0 / (size * size);
        p += (long) (Math.floor(Math.abs(ratio - 50) / 5) * 10);
        return p;
    }
}
