package pl.heartsounds;

import java.util.Arrays;
import java.util.Locale;

/**
 * Everything the report page draws, as one JSON object. Pure calculation, no Android, so the tests run the same code.
 * Input is the unfiltered recording at about 4 kHz and the result of Analysis. Nothing here feeds back into the
 * analysis or the flag. These are acoustic measurements. They are not a diagnosis.
 */
final class Report {
    static final int VERSION = 1;
    /** Band of the phonocardiogram strip, in Hz. */
    static final double P_LO = 40, P_HI = 600;
    static final double STRIP = 10, FULL = 32768;
    private static final String B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private static double db(double ratio) { return 20 * Math.log10(Math.max(ratio, 1e-9)); }
    private static String n(double v, int dec) { return Double.isNaN(v) || Double.isInfinite(v) ? "null" : String.format(Locale.ROOT, "%." + dec + "f", v); }
    private static String arr(double[] v, int dec) { StringBuilder s = new StringBuilder("["); for (int i = 0; i < v.length; i++) { if (i > 0) s.append(','); s.append(n(v[i], dec)); } return s.append(']').toString(); }
    private static String arr(float[] v) { StringBuilder s = new StringBuilder("["); for (int i = 0; i < v.length; i++) { if (i > 0) s.append(','); s.append(Math.round(v[i] * 100) / 100.0); } return s.append(']').toString(); }
    private static String arr(int[] v) { return Arrays.toString(v).replace(" ", ""); }
    private static double median(double[] v, int cnt) { return cnt == 0 ? Double.NaN : Analysis.pct(Arrays.copyOf(v, cnt), 0.5); }
    private static double peak(double[] e, int at, int w) { double m = 0; for (int i = Math.max(0, at - w); i <= Math.min(e.length - 1, at + w); i++) m = Math.max(m, e[i]); return m; }
    private static double mean(double[] e, int from, int to) { double s = 0; for (int i = from; i <= to; i++) s += e[i]; return s / (to - from + 1); }

    /** Signal quality from the 40-200 Hz loudness curve: how far loud moments stand above quiet ones. Same limits as the live label. */
    static String quality(double contrast) { return contrast >= 4 ? "good" : contrast >= 2.5 ? "fair" : "weak"; }

    /**
     * @param x    unfiltered recording at fs (about 4 kHz), 16-bit scale
     * @param rec  JSON object with what is known about the recording (name, date, rate, source, microphone, position, peak, clipped); passed through
     */
    static String json(float[] x, double fs, Analysis a, String rec, String app) {
        StringBuilder o = new StringBuilder(200000);
        o.append("{\"v\":").append(VERSION).append(",\"app\":\"").append(app).append("\",\"rec\":").append(rec == null ? "{}" : rec);
        o.append(",\"seconds\":").append(n(x.length / fs, 2)).append(",\"fs\":").append(n(fs, 0));
        double[] e = a.envLo, m = a.envHi; int ne = e.length; double t0 = a.envT0, step = Analysis.STEP;
        int k = (int) Math.round(fs * step), skip = (int) (0.3 * fs);

        // Quality and noise: loud against quiet moments of the heart-sound band.
        if (ne > 0) {
            double p95 = Analysis.pct(e, 0.95), p30 = Analysis.pct(e, 0.30), p10 = Analysis.pct(e, 0.10), c = p95 / Math.max(p30, 1e-6);
            o.append(",\"quality\":{\"label\":\"").append(p95 <= 0 ? "weak" : quality(c)).append("\",\"contrast\":").append(n(c, 2))
                    .append(",\"snrDb\":").append(n(db(p95 / Math.max(p10, 1e-6)), 1)).append(",\"floorDb\":").append(n(db(p10 / FULL), 1)).append(",\"loudDb\":").append(n(db(p95 / FULL), 1)).append('}');
        } else o.append(",\"quality\":null");

        // Analysis summary. Rate and timing are given only when the beats were found reliably.
        o.append(",\"a\":{\"reliable\":").append(a.reliable).append(",\"why\":\"").append(a.why).append("\",\"rate\":").append(n(a.reliable ? a.rate : Double.NaN, 1))
                .append(",\"cycle\":").append(n(a.reliable ? a.cycle : Double.NaN, 3)).append(",\"s1s2\":").append(n(a.reliable ? a.s1s2 : Double.NaN, 3)).append(",\"spread\":").append(n(a.reliable ? a.spread : Double.NaN, 3))
                .append(",\"beats\":").append(a.beats).append(",\"found\":").append(n(a.found, 3)).append(",\"strength\":").append(n(a.strength, 2))
                .append(",\"sysDb\":").append(n(a.sysDb, 2)).append(",\"sysShare\":").append(n(a.sysShare, 3)).append(",\"diaDb\":").append(n(a.diaDb, 2))
                .append(",\"flag\":").append(Screen.flag(a)).append(",\"limitDb\":").append(n(Screen.SYS_DB, 1)).append('}');

        // Typical beat in both bands.
        o.append(",\"beat\":");
        if (a.avg.length > 0) o.append("{\"step\":").append(step).append(",\"pre\":").append(a.avgPre).append(",\"cycle\":").append(n(a.cycle, 3)).append(",\"s1s2\":").append(n(a.s1s2, 3))
                .append(",\"lo\":{\"mid\":").append(arr(a.avg)).append(",\"q1\":").append(arr(a.avgLo)).append(",\"q3\":").append(arr(a.avgHi))
                .append("},\"hi\":{\"mid\":").append(arr(a.avgM)).append(",\"q1\":").append(arr(a.avgMLo)).append(",\"q3\":").append(arr(a.avgMHi)).append("}}");
        else o.append("null");

        // Per-beat table. Gap levels are relative to the mean peak of the two heart sounds of that beat, in each band.
        int nb = a.beatT.length; double[] gap = new double[nb], sl = new double[nb], dl = new double[nb], sh = new double[nb], dh = new double[nb];
        Arrays.fill(gap, Double.NaN); Arrays.fill(sl, Double.NaN); Arrays.fill(dl, Double.NaN); Arrays.fill(sh, Double.NaN); Arrays.fill(dh, Double.NaN);
        double[] c1 = new double[nb], c2 = new double[nb], c3 = new double[nb], c4 = new double[nb]; int cc = 0;
        int a1 = (int) Math.round(0.07 / step), b1 = (int) Math.round(0.06 / step), w = (int) Math.round(0.04 / step);
        for (int i = 0; i < nb; i++) {
            if (!a.beatOk[i]) continue;
            int p1 = (int) Math.round((a.s1[i] - t0) / step), p2 = (int) Math.round((a.s2[i] - t0) / step);
            gap[i] = a.s2[i] - a.s1[i];
            if (i + 1 >= nb || !a.beatOk[i + 1] || a.beatLen[i] <= 0 || a.beatOdd[i] == 2) continue;
            int p3 = (int) Math.round((a.s1[i + 1] - t0) / step), sA = p1 + a1, sB = p2 - b1, dA = p2 + a1, dB = p3 - b1;
            if (sB - sA < 3 || dB - dA < 3 || p1 < 0 || p3 >= ne) continue;
            double refLo = (peak(e, p1, w) + peak(e, p2, w)) / 2, refHi = (peak(m, p1, w) + peak(m, p2, w)) / 2;
            if (refLo <= 0 || refHi <= 0) continue;
            sl[i] = db(mean(e, sA, sB) / refLo); dl[i] = db(mean(e, dA, dB) / refLo); sh[i] = db(mean(m, sA, sB) / refHi); dh[i] = db(mean(m, dA, dB) / refHi);
            c1[cc] = sl[i]; c2[cc] = dl[i]; c3[cc] = sh[i]; c4[cc] = dh[i]; cc++;
        }
        int[] okI = new int[nb]; for (int i = 0; i < nb; i++) okI[i] = a.beatOk[i] ? 1 : 0;
        o.append(",\"beats\":{\"t\":").append(arr(a.beatT, 3)).append(",\"len\":").append(arr(a.beatLen, 3)).append(",\"gap\":").append(arr(gap, 3)).append(",\"ok\":").append(arr(okI)).append(",\"odd\":").append(arr(a.beatOdd))
                .append(",\"flagDb\":").append(arr(a.beatSys, 1)).append(",\"sysLo\":").append(arr(sl, 1)).append(",\"diaLo\":").append(arr(dl, 1)).append(",\"sysHi\":").append(arr(sh, 1)).append(",\"diaHi\":").append(arr(dh, 1)).append('}');
        o.append(",\"gaps\":{\"n\":").append(cc).append(",\"sysLo\":").append(n(median(c1, cc), 1)).append(",\"diaLo\":").append(n(median(c2, cc), 1)).append(",\"sysHi\":").append(n(median(c3, cc), 1)).append(",\"diaHi\":").append(n(median(c4, cc), 1)).append('}');

        // Loudness of the whole recording, at most 600 points, each the highest value in its stretch.
        if (ne > 0) {
            int g = Math.max(1, (int) Math.ceil(ne / 600.0)), cnt = (ne + g - 1) / g; int[] ov = new int[cnt]; double top = Math.max(Analysis.pct(e, 0.99) * 1.3, 1e-6);
            for (int i = 0; i < cnt; i++) { double mx = 0; for (int j = i * g; j < Math.min(ne, (i + 1) * g); j++) mx = Math.max(mx, e[j]); ov[i] = (int) Math.round(1000 * Math.min(1, mx / top)); }
            o.append(",\"over\":{\"t0\":").append(n(t0, 3)).append(",\"step\":").append(n(g * step, 4)).append(",\"v\":").append(arr(ov)).append('}');
        } else o.append(",\"over\":null");

        // Phonocardiogram strip: the 10 seconds with the most clean beats and no knock, band-passed, lowest and highest value per 5 ms.
        if (ne > 0) {
            int win = (int) Math.min(ne, Math.round(STRIP / step)), hop = (int) Math.round(0.5 / step), bestAt = 0; double bestScore = -1e18;
            for (int s = 0; s + win <= ne; s += hop) {
                double ts = t0 + s * step, te = ts + win * step, score = 0, mx = 0;
                for (int i = 0; i < nb; i++) if (a.beatT[i] >= ts && a.beatT[i] < te) score += a.beatOdd[i] == 2 ? -3 : a.beatOk[i] && a.beatOdd[i] == 0 && a.beatLen[i] > 0 ? 1 : 0;
                for (int i = s; i < s + win; i++) mx = Math.max(mx, e[i]);
                score -= 0.5 * Math.min(1, mx / (20 * Math.max(Analysis.pct(e, 0.97), 1e-9))); // without beats: prefer the stretch without a knock
                if (score > bestScore) { bestScore = score; bestAt = s; }
            }
            Biquad[] hp = Biquad.butter(true, 4, fs, P_LO), lp = Biquad.butter(false, 4, fs, P_HI);
            double[] lo = new double[win], hi = new double[win]; int from = skip + bestAt * k, warm = Math.max(0, from - (int) fs);
            for (int i = warm; i < Math.min(x.length, from + win * k); i++) {
                double y = Biquad.run(lp, Biquad.run(hp, x[i])); if (i < from) continue;
                int b = (i - from) / k; if ((i - from) % k == 0) { lo[b] = y; hi[b] = y; } else { lo[b] = Math.min(lo[b], y); hi[b] = Math.max(hi[b], y); }
            }
            double[] ab = new double[win]; for (int i = 0; i < win; i++) ab[i] = Math.max(-lo[i], hi[i]);
            double scale = Math.max(Analysis.pct(ab, 0.995) * 1.15, 1e-6); int[] mn = new int[win], mx = new int[win];
            for (int i = 0; i < win; i++) { mn[i] = (int) Math.round(1000 * Math.max(-1, lo[i] / scale)); mx[i] = (int) Math.round(1000 * Math.min(1, hi[i] / scale)); }
            o.append(",\"strip\":{\"t0\":").append(n(t0 + bestAt * step, 3)).append(",\"step\":").append(step).append(",\"band\":[").append((int) P_LO).append(',').append((int) P_HI)
                    .append("],\"scale\":").append(n(scale, 1)).append(",\"min\":").append(arr(mn)).append(",\"max\":").append(arr(mx)).append('}');
        } else o.append(",\"strip\":null");

        // S1 and S2 marks for every beat with both sounds found.
        int nm = 0; for (int i = 0; i < a.s1.length; i++) if (i >= nb || a.beatOk[i]) nm++;
        double[] m1 = new double[nm], m2 = new double[nm]; nm = 0;
        for (int i = 0; i < a.s1.length; i++) if (i >= nb || a.beatOk[i]) { m1[nm] = a.s1[i]; m2[nm++] = a.s2[i]; }
        o.append(",\"marks\":{\"s1\":").append(arr(nb > 0 ? m1 : new double[0], 3)).append(",\"s2\":").append(arr(nb > 0 ? m2 : new double[0], 3)).append('}');

        spectra(o, x, fs, skip);

        // All beats lined up on S1, heart-sound band, 10 ms steps, at most 150 beats.
        o.append(",\"overlay\":");
        if (a.avg.length > 0) {
            int pre = (int) Math.round(a.avgPre / step), len = a.avg.length, rows = 0; int[] idx = new int[nb];
            for (int i = 0; i < nb; i++) { int p = (int) Math.round((a.s1[i] - t0) / step) - pre; if (a.beatOk[i] && p >= 0 && p + len <= ne) idx[rows++] = i; }
            int take = Math.min(rows, 150); double top = 1e-6; for (float v : a.avgHi) top = Math.max(top, v); top *= 1.6;
            o.append("{\"step\":").append(2 * step).append(",\"pre\":").append(a.avgPre).append(",\"total\":").append(rows).append(",\"rows\":[");
            int[] odd = new int[take];
            for (int r = 0; r < take; r++) {
                int i = idx[(int) ((long) r * rows / take)], p = (int) Math.round((a.s1[i] - t0) / step) - pre; odd[r] = a.beatOdd[i];
                if (r > 0) o.append(','); o.append('[');
                for (int j = 0; j < len; j += 2) { if (j > 0) o.append(','); o.append(Math.round(1000 * Math.min(1, e[p + j] / top))); }
                o.append(']');
            }
            o.append("],\"odd\":").append(arr(odd)).append('}');
        } else o.append("null");
        return o.append('}').toString();
    }

    /** Average spectrum (whole recording and its quietest quarter) and a reduced spectrogram of 0 to 600 Hz. */
    private static void spectra(StringBuilder o, float[] x, double fs, int skip) {
        // Spectrum: 512-point Hann frames, half overlapping. Level per bin relative to a full-scale sine.
        int N = 512, hop = 256, frames = (x.length - skip - N) / hop + 1;
        if (frames < 4) { o.append(",\"spec\":null,\"sgram\":null"); return; }
        double[] win = new double[N], re = new double[N], im = new double[N]; double ws = 0;
        for (int i = 0; i < N; i++) { win[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / N); ws += win[i]; }
        int top = Math.min(N / 2, (int) Math.floor(1000 * N / fs)), b40 = (int) Math.ceil(40 * N / fs), b200 = (int) Math.floor(200 * N / fs);
        double[][] P = new double[frames][top + 1]; double[] band = new double[frames], total = new double[frames];
        for (int f = 0; f < frames; f++) {
            for (int i = 0; i < N; i++) { re[i] = x[skip + f * hop + i] * win[i]; im[i] = 0; }
            PcgDenoiser.fft(re, im, false);
            for (int b = 0; b <= top; b++) { P[f][b] = re[b] * re[b] + im[b] * im[b]; total[f] += P[f][b]; if (b >= b40 && b <= b200) band[f] += P[f][b]; }
        }
        double cut = Analysis.pct(band, 0.25), norm = 4 / (ws * ws * FULL * FULL); double[] all = new double[top], quiet = new double[top], fr = new double[top]; int nq = 0;
        double loud = Analysis.pct(total, 0.98); int na = 0; // the loudest 2% of frames are left out, so one knock does not shape the average
        for (int f = 0; f < frames; f++) { boolean q = band[f] <= cut, use = total[f] <= loud; if (q) nq++; if (use) na++; for (int b = 1; b <= top; b++) { if (use) all[b - 1] += P[f][b]; if (q) quiet[b - 1] += P[f][b]; } }
        for (int b = 0; b < top; b++) { fr[b] = (b + 1) * fs / N; all[b] = 10 * Math.log10(all[b] / Math.max(na, 1) * norm + 1e-20); quiet[b] = 10 * Math.log10(quiet[b] / Math.max(nq, 1) * norm + 1e-20); }
        o.append(",\"spec\":{\"binHz\":").append(n(fs / N, 2)).append(",\"f\":").append(arr(fr, 1)).append(",\"all\":").append(arr(all, 1)).append(",\"quiet\":").append(arr(quiet, 1)).append('}');

        // Spectrogram: 256-point frames every 16 ms, averaged into at most 600 columns; 64 levels over 50 dB.
        int M = 256, h2 = M / 4, fr2 = (x.length - skip - M) / h2 + 1; double dur = (x.length - skip) / fs;
        int cols = (int) Math.max(1, Math.min(600, Math.ceil(dur / 0.05))), rows = Math.min(M / 2, (int) Math.floor(600 * M / fs)) + 1;
        double[] w2 = new double[M], r2 = new double[M], i2 = new double[M]; for (int i = 0; i < M; i++) w2[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / M);
        double[][] S = new double[cols][rows]; int[] cnt = new int[cols];
        for (int f = 0; f < fr2; f++) {
            for (int i = 0; i < M; i++) { r2[i] = x[skip + f * h2 + i] * w2[i]; i2[i] = 0; }
            PcgDenoiser.fft(r2, i2, false);
            int c = Math.min(cols - 1, (int) ((f * h2 + M / 2) / fs / dur * cols)); cnt[c]++;
            for (int b = 0; b < rows; b++) S[c][b] += r2[b] * r2[b] + i2[b] * i2[b];
        }
        double[] cells = new double[cols * (rows - 1)]; int nc = 0; for (int c = 0; c < cols; c++) for (int b = 1; b < rows; b++) if (cnt[c] > 0) cells[nc++] = S[c][b] / cnt[c];
        double mx = Math.max(Analysis.pct(Arrays.copyOf(cells, nc), 0.995), 1e-20); // the top of the scale is the level only 0.5% of cells exceed, so one knock does not darken the rest
        StringBuilder d = new StringBuilder(cols * rows);
        for (int b = 0; b < rows; b++) for (int c = 0; c < cols; c++) {
            double v = cnt[c] > 0 ? 10 * Math.log10((S[c][b] / cnt[c] + 1e-20) / mx) : -100;
            d.append(B64.charAt((int) Math.round(63 * Math.max(0, Math.min(1, 1 + v / 50)))));
        }
        o.append(",\"sgram\":{\"cols\":").append(cols).append(",\"rows\":").append(rows).append(",\"t0\":").append(n(skip / fs, 3)).append(",\"dt\":").append(n(dur / cols, 4))
                .append(",\"df\":").append(n(fs / M, 3)).append(",\"rangeDb\":50,\"data\":\"").append(d).append("\"}");
    }
}
