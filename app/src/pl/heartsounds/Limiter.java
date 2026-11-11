package pl.heartsounds;

/** Look-ahead limiter, as in Codec2 Recorder: lowers the level before a peak arrives, so it never clips. */
final class Limiter {
    private final double ceiling, release; private final int L;
    private final double[] delay, need, avg; private int p; private double sum, g = 1;

    Limiter(double ceiling, int lookahead, double fs) {
        this.ceiling = ceiling; L = lookahead; release = 1 - Math.exp(-1 / (0.25 * fs)); // about 250 ms back up
        delay = new double[L + 1]; need = new double[L + 1]; avg = new double[L + 1];
        java.util.Arrays.fill(need, 1); java.util.Arrays.fill(avg, 1); sum = L + 1;
    }

    double process(double in) {
        delay[p] = in; need[p] = Math.abs(in) > ceiling ? ceiling / Math.abs(in) : 1;
        double m = 1; for (double v : need) if (v < m) m = v;
        sum += m - avg[p]; avg[p] = m;
        double smooth = sum / (L + 1);
        g = Math.min(smooth, g + release * (1 - g));
        p = (p + 1) % (L + 1);
        double y = delay[p] * g;
        return Math.max(-ceiling, Math.min(ceiling, y));
    }
}
