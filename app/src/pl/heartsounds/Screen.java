package pl.heartsounds;

/**
 * Experimental flag for a murmur-like sound between the first and second heart sound.
 * The limits were chosen on stethoscope recordings (see docs/validation.md), not on phone recordings.
 * 0 = not flagged, 1 = flagged, -1 = cannot tell (beats not found reliably).
 */
final class Screen {
    /** Flag when the sound between S1 and S2 is this many dB above the gap after S2, as the middle value over all beats. */
    static final double SYS_DB = 3;
    static int flag(Analysis a) {
        if (!a.reliable || Double.isNaN(a.sysDb)) return -1;
        return a.sysDb >= SYS_DB ? 1 : 0;
    }
}
