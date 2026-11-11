package pl.heartsounds;

/**
 * Rough signal quality from the band-passed signal before noise reduction: how far the loud moments (the beats)
 * stand above the quiet moments between them over the last 4 seconds. It does not check that the sound is a heart.
 */
final class Quality {
    private final float[] peaks = new float[400]; private final int per; private int fill, idx, count; private float pk;
    Quality(double fs) { per = Math.max(1, (int) Math.round(fs / 100)); }

    void push(double v) {
        float a = (float) Math.abs(v); if (a > pk) pk = a;
        if (++fill >= per) { peaks[idx] = pk; idx = (idx + 1) % peaks.length; if (count < peaks.length) count++; pk = 0; fill = 0; }
    }
    /** Ratio of the 95th to the 30th percentile of 10 ms peaks, or 0 until 2 seconds have been heard. */
    double contrast() {
        if (count < 200) return 0;
        float[] a = java.util.Arrays.copyOf(peaks, count); java.util.Arrays.sort(a);
        return a[(int) (0.95 * (count - 1))] / Math.max(a[(int) (0.30 * (count - 1))], 1e-6);
    }
    String label() { double c = contrast(); return c == 0 ? "" : c >= 4 ? "good" : c >= 2.5 ? "fair" : "weak"; }
}
