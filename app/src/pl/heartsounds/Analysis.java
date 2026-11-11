package pl.heartsounds;

import java.util.Arrays;
import java.util.Locale;

/**
 * Finds the beats in a recording at about 4 kHz and measures them: rate, gap between the two heart sounds,
 * a typical beat, and how much sound there is between the first and second heart sound compared with the
 * quiet part after the second. Pure calculation, no Android, so the same code runs in the tests.
 * These are acoustic measurements. They are not a diagnosis.
 */
final class Analysis {
    static final double STEP = 0.005; // work at 200 points per second
    /** Lowest and highest band used for the sound-between-beats measure, in Hz. */
    static final double M_LO = 150, M_HI = 600;

    boolean reliable; String why = "";
    double seconds, cycle, rate, s1s2, spread, strength, found;
    int beats;
    double[] s1 = new double[0], s2 = new double[0];
    /** Typical beat: middle value, lower and upper quartile of the loudness curve, from avgPre seconds before the first sound. */
    float[] avg = new float[0], avgLo = new float[0], avgHi = new float[0]; double avgPre = 0.15;
    /** Sound in the gap S1 to S2 relative to the gap S2 to S1, in dB: middle value over beats, and the share of beats above the flag limit. */
    double sysDb = Double.NaN, sysShare, diaDb = Double.NaN;

    private static double[] blockMean(double[] x, int k) { double[] o = new double[x.length / k]; for (int i = 0; i < o.length; i++) { double s = 0; for (int j = 0; j < k; j++) s += x[i * k + j]; o[i] = s / k; } return o; }
    private static double pct(double[] a, double p) { if (a.length == 0) return 0; double[] c = a.clone(); Arrays.sort(c); return c[(int) Math.round(p * (c.length - 1))]; }
    private static int argmax(double[] e, int from, int to) { from = Math.max(0, from); to = Math.min(e.length - 1, to); int b = from; for (int i = from; i <= to; i++) if (e[i] > e[b]) b = i; return from > to ? -1 : b; }
    private static double mean(double[] e, int from, int to) { double s = 0; for (int i = from; i <= to; i++) s += e[i]; return s / (to - from + 1); }

    static Analysis run(float[] x, double fs) {
        Analysis a = new Analysis(); a.seconds = x.length / fs;
        int k = (int) Math.round(fs * STEP), skip = (int) (0.3 * fs);
        if (x.length < 6 * fs) { a.why = "the recording is shorter than 6 seconds"; return a; }
        // Loudness curves: heart-sound band for finding beats, higher band for sound between them.
        Biquad[] hp = Biquad.butter(true, 4, fs, 40), lp = Biquad.butter(false, 4, fs, 200), mh = Biquad.butter(true, 4, fs, M_LO), ml = Biquad.butter(false, 4, fs, M_HI);
        Envelope eh = new Envelope(fs, 20), em = new Envelope(fs, 20);
        double[] E = new double[x.length - skip], M = new double[x.length - skip];
        for (int i = 0; i < x.length; i++) {
            double h = eh.put(Biquad.run(lp, Biquad.run(hp, x[i]))), m = em.put(Biquad.run(ml, Biquad.run(mh, x[i])));
            if (i >= skip) { E[i - skip] = h; M[i - skip] = m; }
        }
        double[] e = blockMean(E, k), m = blockMean(M, k); int n = e.length; double t0 = skip / fs;
        double cap = pct(e, 0.97), med = pct(e, 0.5);
        if (cap <= 0) { a.why = "the recording is silent"; return a; }
        double[] ec = new double[n]; double avgE = 0; for (int i = 0; i < n; i++) { ec[i] = Math.min(e[i], cap); avgE += ec[i]; } avgE /= n; // a knock cannot outweigh the beats

        // Beat length from how well the loudness curve matches itself when shifted.
        int lo = (int) (0.30 / STEP), hi = Math.min((int) (1.6 / STEP), n / 3); double[] ac = new double[hi + 2]; double ac0 = 0;
        for (int i = 0; i < n; i++) ac0 += (ec[i] - avgE) * (ec[i] - avgE);
        for (int l = lo - 1; l <= hi + 1; l++) { double s = 0; for (int i = 0; i + l < n; i++) s += (ec[i] - avgE) * (ec[i + l] - avgE); ac[l] = s / ac0 * n / (n - l); }
        double best = 0; for (int l = lo; l <= hi; l++) if (ac[l] > ac[l - 1] && ac[l] >= ac[l + 1] && ac[l] > best) best = ac[l];
        int T = 0; for (int l = lo; l <= hi && T == 0; l++) if (ac[l] > ac[l - 1] && ac[l] >= ac[l + 1] && ac[l] >= 0.75 * best) T = l; // the shortest clear repeat, not a multiple of it
        a.strength = best;
        if (T == 0 || best < 0.12) { a.why = "no regular beat was found"; return a; }

        // Follow one heart sound through the recording, letting the beat length drift.
        int anchor = -1; double top = -1;
        for (int i = 1; i + 1 < n; i++) {
            if (!(ec[i] > ec[i - 1] && ec[i] >= ec[i + 1]) || ec[i] < 1.3 * med) continue;
            double s = 0; for (int j = -3; j <= 3; j++) { int c = i + j * T, b = argmax(ec, c - T / 10, c + T / 10); if (b >= 0 && c >= 0 && c < n) s += ec[b]; }
            if (s > top) { top = s; anchor = i; }
        }
        if (anchor < 0) { a.why = "no clear heart sounds were found"; return a; }
        int[] seq = new int[n / lo + 4]; boolean[] ok = new boolean[seq.length]; int cnt = 0;
        int[] back = new int[seq.length]; boolean[] backOk = new boolean[seq.length]; int nb = 0;
        double tl = T; int cur = anchor;
        while (cur - tl * 1.25 >= 0 && nb < back.length) {
            int c = (int) Math.round(cur - tl), b = argmax(ec, (int) (c - 0.2 * tl), (int) (c + 0.2 * tl)); boolean good = b >= 0 && ec[b] > 1.3 * med;
            int nx = good ? b : c; if (good) tl = Math.max(0.8 * T, Math.min(1.25 * T, 0.7 * tl + 0.3 * (cur - nx)));
            back[nb] = nx; backOk[nb++] = good; cur = nx;
        }
        for (int i = nb - 1; i >= 0; i--) { seq[cnt] = back[i]; ok[cnt++] = backOk[i]; }
        seq[cnt] = anchor; ok[cnt++] = true; tl = T; cur = anchor;
        while (cur + tl * 1.25 < n && cnt < seq.length) {
            int c = (int) Math.round(cur + tl), b = argmax(ec, (int) (c - 0.2 * tl), (int) (c + 0.2 * tl)); boolean good = b >= 0 && ec[b] > 1.3 * med;
            int nx = good ? b : c; if (good) tl = Math.max(0.8 * T, Math.min(1.25 * T, 0.7 * tl + 0.3 * (nx - cur)));
            seq[cnt] = nx; ok[cnt++] = good; cur = nx;
        }
        if (cnt < 5) { a.why = "too few beats were found"; return a; }

        // The other heart sound sits at a steady place inside each beat. The shorter gap is S1 to S2.
        double[] frac = new double[cnt - 1]; int nf = 0; int gap = (int) (0.12 / STEP);
        for (int i = 0; i + 1 < cnt; i++) {
            if (!ok[i] || !ok[i + 1] || seq[i + 1] - seq[i] < 3 * gap) continue;
            int b = argmax(ec, seq[i] + gap, seq[i + 1] - gap); if (b < 0 || ec[b] < 1.2 * med) continue;
            frac[nf++] = (b - seq[i]) / (double) (seq[i + 1] - seq[i]);
        }
        if (nf < 4) { a.why = "the second heart sound was not found"; return a; }
        double f = pct(Arrays.copyOf(frac, nf), 0.5); boolean firstIsS1 = f < 0.5;
        int[] p1 = new int[cnt], p2 = new int[cnt]; boolean[] both = new boolean[cnt]; int nc = 0;
        for (int i = 0; i + 1 < cnt; i++) {
            int len = seq[i + 1] - seq[i], c = seq[i] + (int) Math.round(f * len), b = argmax(ec, Math.max(seq[i] + gap / 2, c - (int) (0.08 * len) - 1), Math.min(seq[i + 1] - gap / 2, c + (int) (0.08 * len) + 1));
            boolean good = ok[i] && ok[i + 1] && b >= 0 && ec[b] > 1.2 * med; if (b < 0) b = c;
            if (firstIsS1) { p1[nc] = seq[i]; p2[nc] = b; both[nc++] = good; }
            else if (i + 1 < cnt) { p1[nc] = b; p2[nc] = seq[i + 1]; both[nc++] = good; }
        }
        // Whole beats: S1, S2 and the next S1 all found.
        double[] iv = new double[nc], sg = new double[nc], sdb = new double[nc], ddb = new double[nc]; int ni = 0, nm = 0, goodBeats = 0, above = 0;
        int a1 = (int) Math.round(0.07 / STEP), b1 = (int) Math.round(0.06 / STEP);
        double noise = pct(m, 0.1);
        for (int i = 0; i < nc; i++) {
            if (both[i]) goodBeats++;
            if (i + 1 >= nc || !both[i] || !both[i + 1]) continue;
            int len = p1[i + 1] - p1[i]; if (len < 0.7 * T || len > 1.4 * T) continue;
            iv[ni] = len * STEP; sg[ni++] = (p2[i] - p1[i]) * STEP;
            int sA = p1[i] + a1, sB = p2[i] - b1, dA = p2[i] + a1, dB = p1[i + 1] - b1;
            if (sB - sA < 3 || dB - dA < 3) continue;
            if (argmax(e, p1[i], p1[i + 1]) >= 0 && e[argmax(e, p1[i], p1[i + 1])] > 4 * cap) continue; // a knock inside this beat
            double sys = mean(m, sA, sB), dia = mean(m, dA, dB);
            sdb[nm] = 20 * Math.log10((sys + 1e-9) / (dia + 1e-9)); ddb[nm] = 20 * Math.log10((dia + 1e-9) / (noise + 1e-9)); if (sdb[nm] >= Screen.SYS_DB) above++; nm++;
        }
        a.found = goodBeats / (double) nc; a.beats = ni;
        a.s1 = new double[nc]; a.s2 = new double[nc]; for (int i = 0; i < nc; i++) { a.s1[i] = t0 + p1[i] * STEP; a.s2[i] = t0 + p2[i] * STEP; }
        if (ni < 5) { a.why = "too few clean beats (" + ni + ")"; return a; }
        double[] ivs = Arrays.copyOf(iv, ni);
        a.cycle = pct(ivs, 0.5); a.rate = 60 / a.cycle; a.spread = (pct(ivs, 0.75) - pct(ivs, 0.25)) / a.cycle; a.s1s2 = pct(Arrays.copyOf(sg, ni), 0.5);
        if (nm >= 5) { a.sysDb = pct(Arrays.copyOf(sdb, nm), 0.5); a.sysShare = above / (double) nm; a.diaDb = pct(Arrays.copyOf(ddb, nm), 0.5); }

        // Typical beat: line the beats up on S1 and take the middle value at each moment, so knocks and noise drop out.
        int pre = (int) Math.round(a.avgPre / STEP), len = pre + (int) Math.round(a.cycle / STEP) + (int) Math.round(0.12 / STEP), rows = 0; double[][] seg = new double[nc][];
        for (int i = 0; i < nc; i++) if (both[i] && p1[i] - pre >= 0 && p1[i] - pre + len <= n) seg[rows++] = Arrays.copyOfRange(e, p1[i] - pre, p1[i] - pre + len);
        if (rows >= 4) {
            a.avg = new float[len]; a.avgLo = new float[len]; a.avgHi = new float[len]; double[] col = new double[rows];
            for (int j = 0; j < len; j++) { for (int r = 0; r < rows; r++) col[r] = seg[r][j]; a.avg[j] = (float) pct(col, 0.5); a.avgLo[j] = (float) pct(col, 0.25); a.avgHi[j] = (float) pct(col, 0.75); }
        }
        if (a.found < 0.6) a.why = "only " + Math.round(100 * a.found) + "% of beats were clear";
        else if (a.spread > 0.5) a.why = "the beat length varies too much to trust";
        else a.reliable = true;
        return a;
    }

    private static String arr(float[] v) { StringBuilder s = new StringBuilder("["); for (int i = 0; i < v.length; i++) { if (i > 0) s.append(','); s.append(Math.round(v[i] * 100) / 100.0); } return s.append(']').toString(); }
    private static String arr(double[] v) { StringBuilder s = new StringBuilder("["); for (int i = 0; i < v.length; i++) { if (i > 0) s.append(','); s.append(Math.round(v[i] * 1000) / 1000.0); } return s.append(']').toString(); }
    private static String num(double v) { return Double.isNaN(v) ? "null" : String.format(Locale.ROOT, "%.3f", v); }
    String json() {
        return "{\"reliable\":" + reliable + ",\"why\":\"" + why + "\",\"seconds\":" + num(seconds) + ",\"rate\":" + num(rate) + ",\"cycle\":" + num(cycle) + ",\"s1s2\":" + num(s1s2) + ",\"spread\":" + num(spread)
                + ",\"beats\":" + beats + ",\"found\":" + num(found) + ",\"sysDb\":" + num(sysDb) + ",\"sysShare\":" + num(sysShare) + ",\"flag\":" + Screen.flag(this)
                + ",\"s1\":" + arr(s1) + ",\"s2\":" + arr(s2) + ",\"step\":" + STEP + ",\"pre\":" + avgPre + ",\"avg\":" + arr(avg) + ",\"lo\":" + arr(avgLo) + ",\"hi\":" + arr(avgHi) + "}";
    }
}
