package pl.heartsounds;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/** Computer-side checks of the heart-sound path. Run with tests/run.sh. Exits with 1 if any check fails. */
public class DspTest {
    static int fails;
    static void check(String what, boolean ok) { System.out.println((ok ? "PASS  " : "FAIL  ") + what); if (!ok) fails++; }

    /** Gain in dB of the chain without noise reduction for a sine at f. */
    static double gainDb(int rate, Cfg c, double f) {
        HeartChain ch = new HeartChain(rate, c); double s = 0; int n = 0; int total = rate * 6;
        for (int i = 0; i < total; i++) if (ch.put(1000 * Math.sin(2 * Math.PI * f * i / rate)) && i > rate * 3) { s += ch.out * ch.out; n++; }
        return 20 * Math.log10(Math.sqrt(s / n) / (1000 / Math.sqrt(2)) + 1e-12);
    }

    /** Synthetic heart sounds: S1 and S2 bursts at the given rate. Not a model of a real heart, only a known signal. */
    static double[] beats(int rate, double secs, double bpm, double amp) {
        double[] x = new double[(int) (rate * secs)]; double period = 60 / bpm;
        for (double t0 = 0.4; t0 < secs - 0.6; t0 += period) {
            burst(x, rate, t0, 0.030, amp, 45, 80);
            burst(x, rate, t0 + 0.30, 0.020, 0.7 * amp, 60, 110);
        }
        return x;
    }
    static void burst(double[] x, int rate, double t0, double width, double amp, double f1, double f2) {
        for (int i = (int) ((t0 - 4 * width) * rate); i < (t0 + 4 * width) * rate; i++) {
            if (i < 0 || i >= x.length) continue; double t = i / (double) rate - t0, e = Math.exp(-0.5 * t * t / (width * width));
            x[i] += amp * e * (0.7 * Math.sin(2 * Math.PI * f1 * t) + 0.3 * Math.sin(2 * Math.PI * f2 * t));
        }
    }
    static double[] run(int rate, Cfg c, double[] x, boolean pre) {
        HeartChain ch = new HeartChain(rate, c); double[] o = new double[x.length / ch.D + 1]; int n = 0;
        for (double v : x) if (ch.put(v)) o[n++] = pre ? ch.pre : ch.out;
        return java.util.Arrays.copyOf(o, n);
    }
    static void writeWav(File f, int rate, double[] x) throws Exception {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(Wav.header(rate, x.length * 2L));
            byte[] b = new byte[x.length * 2];
            for (int i = 0; i < x.length; i++) { int s = (int) Math.max(-32768, Math.min(32767, Math.round(x[i]))); b[2 * i] = (byte) s; b[2 * i + 1] = (byte) (s >> 8); }
            o.write(b);
        }
    }

    public static void main(String[] a) throws Exception {
        Cfg heart = new Cfg(20, 200, 0, 1), wide = new Cfg(20, 600, 0, 1);
        System.out.println("== Frequency response, no noise reduction (dB)");
        System.out.println("   Hz    heart 48k   wide 48k   heart 44.1k");
        double[] fr = {2, 5, 10, 20, 30, 50, 100, 150, 200, 300, 400, 600, 1000, 2000};
        for (double f : fr) System.out.printf("%6.0f %10.1f %10.1f %12.1f%n", f, gainDb(48000, heart, f), gainDb(48000, wide, f), gainDb(44100, heart, f));
        check("divisors: 48 kHz -> 4000 Hz, 44.1 kHz -> 4410 Hz", HeartChain.divisor(48000) == 12 && HeartChain.divisor(44100) == 10);
        check("heart preset flat within 1 dB from 30 to 150 Hz", Math.abs(gainDb(48000, heart, 30)) < 1 && Math.abs(gainDb(48000, heart, 50)) < 1 && Math.abs(gainDb(48000, heart, 150)) < 1);
        check("heart preset -3 dB at 20 and 200 Hz", Math.abs(gainDb(48000, heart, 20) + 3) < 0.5 && Math.abs(gainDb(48000, heart, 200) + 3) < 0.5);
        check("heart preset rejects 5 Hz movement by more than 40 dB", gainDb(48000, heart, 5) < -40);
        check("heart preset rejects 1 kHz by more than 50 dB", gainDb(48000, heart, 1000) < -50);
        check("wide preset flat within 1 dB from 30 to 400 Hz", Math.abs(gainDb(48000, wide, 30)) < 1 && Math.abs(gainDb(48000, wide, 400)) < 1);
        check("wide preset -3 dB at 600 Hz", Math.abs(gainDb(48000, wide, 600) + 3) < 0.7);
        double al1 = gainDb(48000, wide, 3900), al2 = gainDb(48000, wide, 3500), al3 = gainDb(44100, wide, 4310);
        System.out.printf("   alias of 3900 Hz onto 100 Hz: %.1f dB, 3500 onto 500: %.1f dB, 44.1k 4310 onto 100: %.1f dB%n", al1, al2, al3);
        check("aliases into the pass band are below -60 dB", al1 < -60 && al2 < -60 && al3 < -60);
        Cfg hum = new Cfg(20, 200, 50, 1);
        System.out.printf("   50 Hz notch: 50 Hz %.1f dB, 45 Hz %.1f dB, 55 Hz %.1f dB, 100 Hz %.1f dB%n", gainDb(48000, hum, 50), gainDb(48000, hum, 45), gainDb(48000, hum, 55), gainDb(48000, hum, 100));
        check("hum filter removes 50 and 100 Hz by more than 40 dB and costs under 1 dB at 45 and 55 Hz", gainDb(48000, hum, 50) < -40 && gainDb(48000, hum, 100) < -40 && gainDb(48000, hum, 45) > -1 && gainDb(48000, hum, 55) > -1);

        System.out.println("== Noise reduction on synthetic beats (72 per minute, 48 kHz, 30 s)");
        int rate = 48000; double secs = 30; Random R = new Random(7);
        double[] clean = beats(rate, secs, 72, 300);
        double[] ref = run(rate, heart, clean, false);
        System.out.println("   noise        strength  in-band SNR before -> after   quiet-gap noise   beat size kept");
        boolean allOk = true, lightOk = false;
        for (String kind : new String[]{"light hiss", "white hiss", "hiss + drift + 1 kHz tone", "pink-like"}) {
            double[] noisy = clean.clone(); double lp = 0;
            for (int i = 0; i < noisy.length; i++) {
                double g = R.nextGaussian(), v;
                if (kind.equals("light hiss")) v = 150 * g;
                else if (kind.equals("white hiss")) v = 900 * g;
                else if (kind.startsWith("hiss +")) v = 600 * g + 3000 * Math.sin(2 * Math.PI * 1.5 * i / rate) + 500 * Math.sin(2 * Math.PI * 1000 * i / rate);
                else { lp = 0.995 * lp + 0.005 * g * 1500; v = lp; }
                noisy[i] += v;
            }
            double[] bp = run(rate, heart, noisy, false);
            for (double floor : new double[]{0.4, 0.2, 0.1}) {
                double[] dn = run(rate, new Cfg(20, 200, 0, floor), noisy, false);
                int d = PcgDenoiser.N, from = 4000 * 4; // skip the first seconds while the noise estimate forms
                double sp = 0, e0 = 0, e1 = 0, q0 = 0, q1 = 0, pkRef = 0, num = 0, den = 0;
                for (double r : ref) pkRef = Math.max(pkRef, Math.abs(r));
                for (int i = from; i + d < ref.length; i++) {
                    double r = ref[i]; sp += r * r; e0 += (bp[i] - r) * (bp[i] - r); e1 += (dn[i + d] - r) * (dn[i + d] - r);
                    if (Math.abs(r) < 0.5) { q0 += bp[i] * bp[i]; q1 += dn[i + d] * dn[i + d]; }
                    if (Math.abs(r) > 0.2 * pkRef) { num += dn[i + d] * r; den += r * r; } // how much of the beat itself comes through
                }
                double snr0 = 10 * Math.log10(sp / e0), snr1 = 10 * Math.log10(sp / e1), quiet = 10 * Math.log10(q1 / q0);
                System.out.printf("   %-26s %.1f   %5.1f dB -> %5.1f dB          %6.1f dB        %3.0f%%%n", kind, floor, snr0, snr1, quiet, 100 * num / den);
                if (floor == 0.2 && kind.equals("light hiss")) lightOk = snr1 > snr0 + 2 && quiet < -8 && num / den > 0.9 && num / den < 1.05;
                else if (floor == 0.2 && !(snr1 > snr0 + 4 && quiet < -8 && num / den > 0.6 && num / den < 1.05)) allOk = false;
            }
        }
        check("medium strength, light noise: SNR up by more than 2 dB, quiet gaps down by more than 8 dB, beats keep over 90% of their size", lightOk);
        check("medium strength, noise as loud as the beats: SNR up by more than 4 dB, quiet gaps down by more than 8 dB, beats keep over 60%", allOk);
        double[] silent = run(rate, new Cfg(20, 200, 0, 0.2), new double[rate * 3], false); double mx = 0; for (double v : silent) mx = Math.max(mx, Math.abs(v));
        check("silence in gives silence out", mx == 0);
        double[] pass = run(rate, new Cfg(20, 200, 0, 0.9999), clean, false); double err = 0, pw = 0;
        for (int i = 8000; i + PcgDenoiser.N < ref.length; i++) { err += Math.pow(pass[i + PcgDenoiser.N] - ref[i], 2); pw += ref[i] * ref[i]; }
        System.out.printf("   clean signal through noise reduction: error %.1f dB below the signal%n", -10 * Math.log10(err / pw));
        check("a clean signal passes noise reduction almost unchanged (error below -25 dB)", 10 * Math.log10(err / pw) < -25);

        System.out.println("== Files");
        File dir = new File(a.length > 0 ? a[0] : "."), raw = new File(dir, "t_raw.wav"), out = new File(dir, "t_out.wav"), csv = new File(dir, "t.csv");
        double[] rec = clean.clone(); for (int i = 0; i < rec.length; i++) rec[i] = rec[i] / 10 + 20 * R.nextGaussian(); // quiet recording: beats 30 units
        for (int i = rate * 10; i < rate * 10 + 200; i++) rec[i] += 20000; // one knock on the phone
        writeWav(raw, rate, rec);
        check("WAV header: rate and length read back", Wav.rate(raw) == rate && Wav.samples(raw) == rec.length);
        long t0 = System.nanoTime(); double g = Render.wav(raw, out, new Cfg(20, 200, 0, 0.2)); double ms = (System.nanoTime() - t0) / 1e6;
        byte[] ob = java.nio.file.Files.readAllBytes(out.toPath());
        int pk = 0, big = 0; for (int i = Wav.HEADER; i + 1 < ob.length; i += 2) { int v = Math.abs((short) ((ob[i] & 255) | (ob[i + 1] << 8))); pk = Math.max(pk, v); if (v > 8000) big++; }
        System.out.printf("   filtered listening file: %d Hz, gain %.0fx, peak %d, %d samples above 8000, rendered 30 s in %.0f ms%n", Wav.rate(out), g, pk, big, ms);
        check("filtered file is 4000 Hz and never exceeds the limiter ceiling", Wav.rate(out) == 4000 && pk <= Render.CEILING);
        check("a knock does not set the level: quiet beats are raised above 8000", g > 100 && big > 500);
        check("rendering 30 s takes under 3 s on this computer", ms < 3000);
        double g2 = Render.wav(raw, out, new Cfg(0, 0, 0, 1));
        check("raw listening file keeps the original rate and length", Wav.rate(out) == rate && Wav.samples(out) == rec.length && g2 > 1);
        Render.csv(raw, csv, new Cfg(20, 200, 0, 0.2));
        java.util.List<String> lines = java.nio.file.Files.readAllLines(csv.toPath());
        check("CSV has a header and one row per filtered sample, with the smooth trace", lines.get(0).equals("time_s,pcg_heart20-200,smooth_trace") && lines.get(4001).split(",").length == 3 && lines.size() == rec.length / 12 + 1 && lines.get(4001).startsWith("1.00000,"));
        byte[] before = java.nio.file.Files.readAllBytes(raw.toPath()); Render.wav(raw, out, new Cfg(20, 600, 50, 0.1));
        check("the raw recording is unchanged by rendering", java.util.Arrays.equals(before, java.nio.file.Files.readAllBytes(raw.toPath())));

        System.out.println("== Smooth trace");
        Envelope ev = new Envelope(4000); double[] tr = new double[ref.length]; for (int i = 0; i < ref.length; i++) tr[i] = ev.put(ref[i]);
        int humps = 0; double tmax = 0; for (double v : tr) tmax = Math.max(tmax, v);
        for (int i = 1; i + 1 < tr.length; i++) if (tr[i] > 0.25 * tmax && tr[i] >= tr[i - 1] && tr[i] > tr[i + 1]) humps++;
        int expect = 0; for (double tb = 0.4; tb < secs - 0.6; tb += 60 / 72.0) expect += 2;
        System.out.printf("   humps in the smooth trace: %d, heart sounds in the signal: %d%n", humps, expect);
        check("smooth trace has exactly one hump per heart sound", humps == expect);
        System.out.println("== Quality and scope");
        Quality qg = new Quality(4000), qn = new Quality(4000);
        double[] good = run(rate, heart, clean, true); for (double v : good) qg.push(v + 2 * R.nextGaussian());
        for (int i = 0; i < 4000 * 6; i++) qn.push(50 * R.nextGaussian());
        System.out.printf("   contrast: beats %.1f, noise only %.1f%n", qg.contrast(), qn.contrast());
        check("beats read as good, plain noise reads as weak", qg.label().equals("good") && qn.label().equals("weak"));
        Scope.reset(40); for (int i = 0; i < 4000; i++) Scope.push(i % 40 == 0 ? -5 : 3, 1.5);
        String s = Scope.since(98);
        check("scope returns only new buckets", s.equals("{\"n\":100,\"d\":[-5.0,3.0,1.5,-5.0,3.0,1.5]}"));
        raw.delete(); out.delete(); csv.delete();
        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
