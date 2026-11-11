package pl.heartsounds;

/** The last 12 seconds of the displayed signal, one bucket per 10 ms: lowest value, highest value and smooth trace. Fed by recording or playback. */
final class Scope {
    static final int CAP = 1200;
    private static final float[] mn = new float[CAP], mx = new float[CAP], en = new float[CAP];
    private static long total; private static int per = 40, fill; private static float lo, hi, ev;

    static synchronized void reset(int samplesPerBucket) { per = Math.max(1, samplesPerBucket); total = 0; fill = 0; }
    static synchronized void push(double v, double envelope) {
        float f = (float) v; ev = (float) envelope;
        if (fill == 0) { lo = f; hi = f; } else { if (f < lo) lo = f; if (f > hi) hi = f; }
        if (++fill >= per) { int i = (int) (total % CAP); mn[i] = lo; mx[i] = hi; en[i] = ev; total++; fill = 0; }
    }
    /** JSON with the bucket count so far and the buckets after index since: {"n":count,"d":[min,max,trace,...]}. */
    static synchronized String since(long since) {
        long from = Math.max(Math.min(since, total), total - CAP);
        StringBuilder s = new StringBuilder(64 + (int) (total - from) * 18).append("{\"n\":").append(total).append(",\"d\":[");
        for (long k = from; k < total; k++) {
            int i = (int) (k % CAP);
            if (k > from) s.append(',');
            s.append(Math.round(mn[i] * 100) / 100.0).append(',').append(Math.round(mx[i] * 100) / 100.0).append(',').append(Math.round(en[i] * 100) / 100.0);
        }
        return s.append("]}").toString();
    }
}
