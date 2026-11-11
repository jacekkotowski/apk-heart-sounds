package pl.heartsounds;

/**
 * Smooth trace of how loud the heart sound is over time: the signal's size, low-passed at 15 Hz.
 * Each beat shows as a smooth hump, which is easier to read than the raw vibration. It is a loudness curve, not an ECG.
 */
final class Envelope {
    private final Biquad[] lp;
    Envelope(double fs) { this(fs, 15); }
    Envelope(double fs, double cutoff) { lp = Biquad.butter(false, 4, fs, cutoff); }
    double put(double x) { return Math.max(0, Biquad.run(lp, Math.abs(x))); }
}
