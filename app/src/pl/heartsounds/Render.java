package pl.heartsounds;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/** Turns a saved raw recording into a filtered signal, a listening WAV or a CSV. The raw file is never changed. */
final class Render {
    static final double TARGET = 12000, CEILING = 30000, MAX_GAIN = 3000, PCT = 0.99;

    private interface Sink { void put(short v) throws IOException; }
    private static int read(File raw, Sink s) throws IOException {
        int rate = Wav.rate(raw); if (rate < 0) throw new IOException("this is not a recording from this app");
        try (InputStream in = new BufferedInputStream(new FileInputStream(raw), 1 << 16)) {
            if (in.skip(Wav.HEADER) != Wav.HEADER) throw new IOException("the file is too short");
            int a, b; while ((a = in.read()) >= 0 && (b = in.read()) >= 0) s.put((short) (a | (b << 8)));
        }
        return rate;
    }

    /** Filtered signal in microphone units (16-bit scale), with no level change. fsOut[0] receives its sample rate. */
    static float[] signal(File raw, Cfg c, int[] fsOut) throws IOException { return signal(raw, c, fsOut, null); }
    /** As above. stats, when given, receives the highest raw sample size and the number of raw samples at full scale. */
    static float[] signal(File raw, Cfg c, int[] fsOut, long[] stats) throws IOException {
        int rate = Wav.rate(raw); if (rate < 0) throw new IOException("this is not a recording from this app");
        HeartChain ch = new HeartChain(rate, c); fsOut[0] = (int) Math.round(ch.fs);
        float[] out = new float[(int) (Wav.samples(raw) / ch.D) + 1]; int[] n = {0}; long[] st = {0, 0};
        read(raw, v -> { int m = Math.abs((int) v); if (m > st[0]) st[0] = m; if (m >= 32767) st[1]++; if (ch.put(v) && n[0] < out.length) out[n[0]++] = (float) ch.out; });
        if (stats != null) { stats[0] = st[0]; stats[1] = st[1]; }
        return java.util.Arrays.copyOf(out, n[0]);
    }

    /**
     * One gain for the whole recording, so beats keep their relative size. It is set from the level that 99% of
     * samples stay under, which sits a little below the beat peaks. A knock on the phone lasting under 1% of the
     * recording therefore does not make the heart sounds quiet; the limiter catches the knock.
     */
    static double gain(float[] x, int fs) {
        int skip = Math.min(x.length / 2, (int) (0.3 * fs)); // the filters are still settling here
        float[] a = new float[x.length - skip]; for (int i = 0; i < a.length; i++) a[i] = Math.abs(x[i + skip]);
        if (a.length == 0) return 1;
        java.util.Arrays.sort(a);
        return Math.min(MAX_GAIN, TARGET / Math.max(a[(int) (PCT * (a.length - 1))], 1e-3));
    }

    /**
     * Moves every frequency up by the same number of Hz, keeping the timing. Heart sounds at 50-200 Hz land at
     * 300-450 Hz, where ears and phone speakers work. The result is altered sound, for listening only.
     */
    static float[] shiftUp(float[] x, int fs, double hz) {
        int n = 1; while (n < x.length) n <<= 1;
        double[] re = new double[n], im = new double[n]; for (int i = 0; i < x.length; i++) re[i] = x[i];
        PcgDenoiser.fft(re, im, false);
        int k = (int) Math.round(hz * n / fs), half = n / 2;
        double[] r2 = new double[n], i2 = new double[n];
        for (int b = 1; b + k < half; b++) { r2[b + k] = re[b]; i2[b + k] = im[b]; r2[n - b - k] = re[b]; i2[n - b - k] = -im[b]; }
        PcgDenoiser.fft(r2, i2, true);
        float[] y = new float[x.length]; for (int i = 0; i < y.length; i++) y[i] = (float) r2[i];
        return y;
    }

    /** Writes a WAV for listening: filtered at about 4 kHz, or in Raw mode the original rate, raised to a comfortable level. shiftHz above 0 raises the pitch (not in Raw mode). Returns the gain used. */
    static double wav(File raw, File out, Cfg c, double shiftHz) throws IOException {
        if (!c.raw()) {
            int[] fs = {0}; float[] x = signal(raw, c, fs); if (shiftHz > 0) x = shiftUp(x, fs[0], shiftHz); double g = gain(x, fs[0]);
            Limiter lim = new Limiter(CEILING, 32, fs[0]);
            try (OutputStream o = new BufferedOutputStream(new FileOutputStream(out), 1 << 16)) {
                o.write(Wav.header(fs[0], x.length * 2L));
                for (float v : x) { int s = (int) Math.round(lim.process(v * g)); o.write(s & 255); o.write((s >> 8) & 255); }
            }
            return g;
        }
        int[] hist = new int[32769]; long[] n = {0};
        int rate = read(raw, v -> { hist[Math.min(32768, Math.abs((int) v))]++; n[0]++; });
        long want = (long) (PCT * n[0]), acc = 0; int p = 0; while (p < 32768 && (acc += hist[p]) < want) p++;
        double g = Math.min(MAX_GAIN, TARGET / Math.max(p, 1));
        Limiter lim = new Limiter(CEILING, 64, rate);
        try (OutputStream o = new BufferedOutputStream(new FileOutputStream(out), 1 << 16)) {
            o.write(Wav.header(rate, n[0] * 2));
            read(raw, v -> { int s = (int) Math.round(lim.process(v * g)); o.write(s & 255); o.write((s >> 8) & 255); });
        }
        return g;
    }

    /** Graph data: time in seconds, filtered value and smooth trace, both in microphone units. */
    static void csv(File raw, File out, Cfg c) throws IOException {
        int[] fs = {0}; float[] x = signal(raw, c, fs);
        try (OutputStream o = new BufferedOutputStream(new FileOutputStream(out), 1 << 16)) {
            o.write(("time_s,pcg_" + c.tag() + ",smooth_trace\n").getBytes()); Envelope env = new Envelope(fs[0]);
            for (int i = 0; i < x.length; i++) o.write(String.format(Locale.ROOT, "%.5f,%.3f,%.3f\n", i / (double) fs[0], x[i], env.put(x[i])).getBytes());
        }
    }
}
