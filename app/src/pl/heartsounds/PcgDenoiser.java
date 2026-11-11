package pl.heartsounds;

/**
 * Noise reduction for heart sounds at about 4 kHz.
 * Heart sounds are short bursts with a quiet gap in every beat, so the steady noise in each frequency bin is
 * the lowest level seen over the last 3 seconds. A Wiener gain then lowers each bin towards that noise.
 * Steady noise (hiss, hum, room and electronics noise) goes down. Sounds that come and go, such as speech or
 * rubbing, are not removed. A sound that never pauses, such as a continuous murmur, would be treated as noise.
 */
final class PcgDenoiser {
    static final int N = 256, HOP = 64, BINS = N / 2 + 1;
    private static final int WINDOW = 47, WINDOWS = 4; // 4 x 0.75 s of minimum tracking at 4 kHz
    private final double[] win = new double[N], in = new double[N], ola = new double[N], outHop = new double[HOP], re = new double[N], im = new double[N];
    private final double[] S = new double[BINS], Smin = new double[BINS], Stmp = new double[BINS], noise = new double[BINS], prevClean = new double[BINS], g = new double[BINS];
    private final double[][] hist = new double[WINDOWS][BINS];
    private int fill, hIdx; private long hops;
    /** Lowest gain. 0.4 is gentle, 0.1 is strong. */
    final double floor;

    PcgDenoiser(double floor) {
        this.floor = floor;
        for (int i = 0; i < N; i++) win[i] = Math.sin(Math.PI * (i + 0.5) / N);
        java.util.Arrays.fill(Smin, Double.MAX_VALUE); java.util.Arrays.fill(Stmp, Double.MAX_VALUE);
        for (double[] h : hist) java.util.Arrays.fill(h, Double.MAX_VALUE);
    }

    /** Takes one sample and returns one sample, delayed by N samples (64 ms at 4 kHz). */
    double put(double x) {
        in[N - HOP + fill] = x;
        double y = outHop[fill];
        if (++fill == HOP) { fill = 0; hop(); System.arraycopy(in, HOP, in, 0, N - HOP); }
        return y;
    }

    private void hop() {
        hops++;
        for (int i = 0; i < N; i++) { re[i] = in[i] * win[i]; im[i] = 0; }
        fft(re, im, false);
        boolean windowEnd = hops % WINDOW == 0;
        for (int k = 0; k < BINS; k++) {
            double p = re[k] * re[k] + im[k] * im[k];
            S[k] = hops == 1 ? p : 0.7 * S[k] + 0.3 * p;
            if (hops > 4) { if (S[k] < Stmp[k]) Stmp[k] = S[k]; if (S[k] < Smin[k]) Smin[k] = S[k]; } // the first frames are still filling
            double target = Smin[k] == Double.MAX_VALUE ? S[k] : 2.5 * Smin[k]; // smoothed minima sit below the mean power
            noise[k] = hops <= 5 ? target : 0.9 * noise[k] + 0.1 * target;
            double nn = noise[k] + 1e-9, post = p / nn;
            double prio = 0.92 * prevClean[k] / nn + 0.08 * Math.max(post - 1, 0);
            g[k] = Math.max(floor, prio / (prio + 1));
            prevClean[k] = g[k] * g[k] * p;
        }
        if (windowEnd) {
            System.arraycopy(Stmp, 0, hist[hIdx], 0, BINS); hIdx = (hIdx + 1) % WINDOWS;
            for (int k = 0; k < BINS; k++) { double m = Double.MAX_VALUE; for (double[] h : hist) if (h[k] < m) m = h[k]; Smin[k] = m; Stmp[k] = Double.MAX_VALUE; }
        }
        // Smooth the gain across neighbouring bins so single bins do not chirp.
        double prev = g[0];
        for (int k = 0; k < BINS; k++) {
            double next = k + 1 < BINS ? g[k + 1] : g[k], sm = 0.25 * prev + 0.5 * g[k] + 0.25 * next; prev = g[k];
            re[k] *= sm; im[k] *= sm;
            if (k > 0 && k < N / 2) { re[N - k] = re[k]; im[N - k] = -im[k]; }
        }
        fft(re, im, true);
        for (int i = 0; i < N; i++) ola[i] += re[i] * win[i] * 0.5; // four overlapping sine-squared windows add up to 2
        System.arraycopy(ola, 0, outHop, 0, HOP);
        System.arraycopy(ola, HOP, ola, 0, N - HOP); java.util.Arrays.fill(ola, N - HOP, N, 0);
    }

    static void fft(double[] re, double[] im, boolean inverse) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1; for (; (j & bit) != 0; bit >>= 1) j ^= bit; j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * Math.PI / len * (inverse ? 1 : -1), wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int j = 0; j < len / 2; j++) {
                    int a = i + j, b = a + len / 2;
                    double xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi; re[a] += xr; im[a] += xi;
                    double t = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = t;
                }
            }
        }
        if (inverse) for (int i = 0; i < n; i++) { re[i] /= n; im[i] /= n; }
    }
}
