package pl.heartsounds;

/**
 * The heart-sound path: microphone rate -> about 4 kHz -> band-pass -> optional mains-hum notch -> noise reduction.
 * No automatic level and no gate, so the relative loudness of the beats is kept.
 */
final class HeartChain {
    final int D; final double fs;
    private final Biquad[] aa, hp, lp, notch; private final Biquad dc; private final PcgDenoiser den; private int phase;
    /** Latest output sample, and the same sample before noise reduction. */
    double out, pre;

    HeartChain(int inRate, Cfg c) {
        D = divisor(inRate); fs = inRate / (double) D;
        aa = D > 1 ? Biquad.butter(false, 8, inRate, 0.3 * fs) : new Biquad[0]; // keeps aliases at least 70 dB below the 20-600 Hz band
        if (c.raw()) { hp = lp = notch = null; den = null; dc = Biquad.highpass(fs, 2, Math.sqrt(0.5)); return; }
        dc = null;
        hp = Biquad.butter(true, 4, fs, c.lo); lp = Biquad.butter(false, 4, fs, c.hi);
        notch = c.hum <= 0 ? new Biquad[0] : 2 * c.hum < c.hi ? new Biquad[]{Biquad.notch(fs, c.hum, 20), Biquad.notch(fs, 2 * c.hum, 30)} : new Biquad[]{Biquad.notch(fs, c.hum, 20)};
        den = c.floor < 1 ? new PcgDenoiser(c.floor) : null;
    }

    /** Largest whole divisor that leaves at least 4000 Hz: 12 for 48 kHz, 10 for 44.1 kHz. */
    static int divisor(int rate) { for (int d = rate / 4000; d > 1; d--) if (rate % d == 0) return d; return 1; }

    /** Feeds one microphone sample. Returns true when a new output sample is ready in out. */
    boolean put(double x) {
        double y = Biquad.run(aa, x);
        if (++phase < D) return false;
        phase = 0;
        if (dc != null) { pre = out = dc.process(y); return true; }
        y = Biquad.run(notch, Biquad.run(lp, Biquad.run(hp, y)));
        pre = y; out = den != null ? den.put(y) : y;
        return true;
    }
}
