package com.gikoko.gitoolbox;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Track-level remuxing engine used by audio + video tools:
 * cut (time range), drop-audio, drop-video, rotation metadata, concat merge.
 * Copies compressed samples directly — no re-encode, so quality is lossless.
 */
public final class AudioVideoMux {

    public static final int MODE_ALL = 0;
    public static final int MODE_AUDIO = 1;
    public static final int MODE_VIDEO = 2;

    private AudioVideoMux() {
    }

    /** Boolean convenience overload used by AudioToolActivity. */
    public static long mux(File in, File out, boolean audioOnly, long startUs, long endUs,
                           Integer orientationHint) throws Exception {
        return mux(in, out, audioOnly ? MODE_AUDIO : MODE_ALL, startUs, endUs, orientationHint);
    }

    /**
     * Copy selected tracks from in → out between startUs and endUs.
     * orientationHint (0/90/180/270) is written as rotation metadata.
     * Returns number of samples written (0 = no matching track).
     */
    public static long mux(File in, File out, int mode, long startUs, long endUs,
                           Integer orientationHint) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(in.getAbsolutePath());
        List<Integer> sel = new ArrayList<Integer>();
        for (int i = 0; i < ex.getTrackCount(); i++) {
            MediaFormat f = ex.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime == null) continue;
            boolean audio = mime.startsWith("audio/");
            boolean video = mime.startsWith("video/");
            if ((mode == MODE_AUDIO && audio) || (mode == MODE_VIDEO && video)
                    || (mode == MODE_ALL && (audio || video))) {
                sel.add(i);
            }
        }
        if (sel.isEmpty()) {
            ex.release();
            return 0;
        }
        MediaMuxer mux = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        if (orientationHint != null) {
            mux.setOrientationHint(orientationHint);
        }
        int[] map = new int[sel.size()];
        for (int k = 0; k < sel.size(); k++) {
            ex.selectTrack(sel.get(k));
            map[k] = mux.addTrack(ex.getTrackFormat(sel.get(k)));
        }
        mux.start();
        ByteBuffer buf = ByteBuffer.allocate(1 << 20);
        MediaMuxer.BufferInfoShim info = new MediaMuxer.BufferInfoShim();
        long written = 0;
        if (startUs > 0) {
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        }
        while (true) {
            int srcTrack = ex.getSampleTrackIndex();
            if (srcTrack < 0) break;
            long t = ex.getSampleTime();
            if (t < 0) break;
            if (t > endUs) break;
            int size = ex.readSampleData(buf, 0);
            if (size < 0) break;
            int outTrack = -1;
            for (int k = 0; k < sel.size(); k++) {
                if (sel.get(k) == srcTrack) {
                    outTrack = map[k];
                    break;
                }
            }
            if (outTrack >= 0) {
                info.offset = 0;
                info.size = size;
                long pts = t - Math.min(startUs, t);
                if (pts < 0) pts = 0;
                info.presentationTimeUs = pts;
                info.flags = (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                        ? android.media.MediaCodec.BufferInfo.FLAG_KEY_FRAME : 0;
                mux.writeSampleData(outTrack, buf, info.info());
                written++;
            }
            ex.advance();
        }
        mux.stop();
        mux.release();
        ex.release();
        return written;
    }

    /**
     * Concat two files of the SAME codec into out.
     * audioOnly=true merges the audio track, else merges the video track (audio dropped).
     */
    public static void merge(File a, File b, File out, boolean audioOnly) throws Exception {
        MediaExtractor exA = new MediaExtractor();
        exA.setDataSource(a.getAbsolutePath());
        MediaExtractor exB = new MediaExtractor();
        exB.setDataSource(b.getAbsolutePath());
        int tA = pick(exA, audioOnly);
        int tB = pick(exB, audioOnly);
        if (tA < 0 || tB < 0) {
            exA.release();
            exB.release();
            throw new Exception(audioOnly ? "One of the files has no audio track" : "One of the files has no video track");
        }
        exA.selectTrack(tA);
        exB.selectTrack(tB);
        MediaFormat fA = exA.getTrackFormat(tA);
        MediaFormat fB = exB.getTrackFormat(tB);
        String mA = fA.getString(MediaFormat.KEY_MIME);
        String mB = fB.getString(MediaFormat.KEY_MIME);
        if (!mA.equals(mB)) {
            exA.release();
            exB.release();
            throw new Exception("Formats differ (" + mA + " vs " + mB + ") — both files must be the same format");
        }
        if (!audioOnly) {
            int wA = fA.containsKey(MediaFormat.KEY_WIDTH) ? fA.getInteger(MediaFormat.KEY_WIDTH) : 0;
            int wB = fB.containsKey(MediaFormat.KEY_WIDTH) ? fB.getInteger(MediaFormat.KEY_WIDTH) : 0;
            int hA = fA.containsKey(MediaFormat.KEY_HEIGHT) ? fA.getInteger(MediaFormat.KEY_HEIGHT) : 0;
            int hB = fB.containsKey(MediaFormat.KEY_HEIGHT) ? fB.getInteger(MediaFormat.KEY_HEIGHT) : 0;
            if (wA != wB || hA != hB) {
                exA.release();
                exB.release();
                throw new Exception("Videos must have the same resolution (" + wA + "x" + hA + " vs " + wB + "x" + hB + ")");
            }
        }
        long durA = fA.containsKey(MediaFormat.KEY_DURATION) ? fA.getLong(MediaFormat.KEY_DURATION) : 0;
        MediaMuxer mux = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int trackOut = mux.addTrack(fA);
        mux.start();
        ByteBuffer buf = ByteBuffer.allocate(1 << 20);
        MediaMuxer.BufferInfoShim info = new MediaMuxer.BufferInfoShim();
        long offset = 0;
        // file A
        while (true) {
            int size = exA.readSampleData(buf, 0);
            if (size < 0) break;
            info.offset = 0;
            info.size = size;
            info.presentationTimeUs = exA.getSampleTime() + offset;
            info.flags = (exA.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                    ? android.media.MediaCodec.BufferInfo.FLAG_KEY_FRAME : 0;
            mux.writeSampleData(trackOut, buf, info.info());
            exA.advance();
        }
        // file B appended
        offset = durA > 0 ? durA + 300000 : 0;
        while (true) {
            int size = exB.readSampleData(buf, 0);
            if (size < 0) break;
            info.offset = 0;
            info.size = size;
            info.presentationTimeUs = exB.getSampleTime() + offset;
            info.flags = (exB.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                    ? android.media.MediaCodec.BufferInfo.FLAG_KEY_FRAME : 0;
            mux.writeSampleData(trackOut, buf, info.info());
            exB.advance();
        }
        mux.stop();
        mux.release();
        exA.release();
        exB.release();
    }

    private static int pick(MediaExtractor ex, boolean audioOnly) {
        for (int i = 0; i < ex.getTrackCount(); i++) {
            MediaFormat f = ex.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime == null) continue;
            if (audioOnly && mime.startsWith("audio/")) return i;
            if (!audioOnly && mime.startsWith("video/")) return i;
        }
        return -1;
    }

    /** Small wrapper so the BufferInfo import stays in one place. */
    static final class BufferInfoShim {
        final android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();
        int offset;
        int size;
        long presentationTimeUs;
        int flags;

        android.media.MediaCodec.BufferInfo info() {
            info.set(offset, size, presentationTimeUs, flags);
            return info;
        }
    }
}
