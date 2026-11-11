package pl.heartsounds;

/** Filter settings. lo of 0 means Raw: no band-pass and no noise reduction. */
final class Cfg {
    final double lo, hi, hum, floor;
    Cfg(double lo, double hi, double hum, double floor) { this.lo = lo; this.hi = hi; this.hum = hum; this.floor = floor; }
    boolean raw() { return lo <= 0; }
    String tag() { return raw() ? "raw" : "heart" + Math.round(lo) + "-" + Math.round(hi); }
}
