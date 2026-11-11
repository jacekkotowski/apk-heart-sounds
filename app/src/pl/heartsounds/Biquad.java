package pl.heartsounds;

/** One second-order filter section, and Butterworth filters built as a cascade of them. */
final class Biquad {
    private final double b0, b1, b2, a1, a2; private double z1, z2;

    private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
        this.b0 = b0 / a0; this.b1 = b1 / a0; this.b2 = b2 / a0; this.a1 = a1 / a0; this.a2 = a2 / a0;
    }

    double process(double x) { double y = b0 * x + z1; z1 = b1 * x - a1 * y + z2; z2 = b2 * x - a2 * y; return y; }

    static Biquad lowpass(double fs, double f, double q) {
        double w = 2 * Math.PI * f / fs, al = Math.sin(w) / (2 * q), c = Math.cos(w);
        return new Biquad((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al);
    }
    static Biquad highpass(double fs, double f, double q) {
        double w = 2 * Math.PI * f / fs, al = Math.sin(w) / (2 * q), c = Math.cos(w);
        return new Biquad((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al);
    }
    static Biquad notch(double fs, double f, double q) {
        double w = 2 * Math.PI * f / fs, al = Math.sin(w) / (2 * q), c = Math.cos(w);
        return new Biquad(1, -2 * c, 1, 1 + al, -2 * c, 1 - al);
    }

    /** Butterworth filter of an even order: maximally flat in the pass band, -3 dB at f. */
    static Biquad[] butter(boolean high, int order, double fs, double f) {
        Biquad[] s = new Biquad[order / 2];
        for (int k = 0; k < s.length; k++) {
            double q = 1 / (2 * Math.cos(Math.PI * (2 * k + 1) / (2.0 * order)));
            s[k] = high ? highpass(fs, f, q) : lowpass(fs, f, q);
        }
        return s;
    }
    static double run(Biquad[] s, double x) { for (Biquad b : s) x = b.process(x); return x; }
}
