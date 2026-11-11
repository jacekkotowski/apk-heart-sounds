package pl.heartsounds;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** 16-bit mono WAV with the plain 44-byte header this app writes. */
final class Wav {
    static final int HEADER = 44;
    static byte[] header(int rate, long dataBytes) {
        byte[] h = new byte[HEADER]; java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(h).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()); b.putInt((int) (36 + dataBytes)); b.put("WAVEfmt ".getBytes()); b.putInt(16); b.putShort((short) 1); b.putShort((short) 1);
        b.putInt(rate); b.putInt(rate * 2); b.putShort((short) 2); b.putShort((short) 16); b.put("data".getBytes()); b.putInt((int) dataBytes);
        return h;
    }
    /** Sample rate, or -1 when the file is not one of ours. The length comes from the file size, so a recording cut short still opens. */
    static int rate(File f) {
        try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
            byte[] h = new byte[HEADER]; if (in.read(h) != HEADER) return -1;
            java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(h).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            if (h[0] != 'R' || h[1] != 'I' || h[8] != 'W' || h[9] != 'A' || b.getShort(20) != 1 || b.getShort(22) != 1 || b.getShort(34) != 16) return -1;
            int r = b.getInt(24); return r >= 4000 && r <= 192000 ? r : -1;
        } catch (IOException e) { return -1; }
    }
    static long samples(File f) { return Math.max(0, (f.length() - HEADER) / 2); }
    /** Writes the final sizes once recording has ended. */
    static void finish(File f, int rate) throws IOException {
        try (RandomAccessFile o = new RandomAccessFile(f, "rw")) { o.write(header(rate, samples(f) * 2)); }
    }
}
