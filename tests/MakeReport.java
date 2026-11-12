package pl.heartsounds;

import java.io.File;
import java.nio.file.Files;

/**
 * Makes the report JSON for one WAV on a computer, the same way the app does, so tests/report.js can render a real recording:
 * java -cp out/test pl.heartsounds.MakeReport recording.wav out.json   (after bash tests/run.sh). Never commit the recording or the JSON.
 */
public class MakeReport {
    public static void main(String[] a) throws Exception {
        File f = new File(a[0]); int[] r = {0}; float[] raw = Eval.wav(f, r);
        int d = HeartChain.divisor(r[0]); HeartChain ch = new HeartChain(r[0], new Cfg(0, 0, 0, 1));
        float[] x = new float[raw.length / d + 1]; int n = 0; long peak = 0, clip = 0;
        for (float v : raw) { long m = (long) Math.abs(v); if (m > peak) peak = m; if (m >= 32767) clip++; if (ch.put(v) && n < x.length) x[n++] = (float) ch.out; }
        x = java.util.Arrays.copyOf(x, n);
        long t = System.nanoTime(); Analysis an = Analysis.run(x, ch.fs);
        String rec = "{\"name\":\"" + f.getName().replace("\"", "") + "\",\"date\":\"\",\"rate\":" + r[0] + ",\"source\":\"\",\"mic\":\"\",\"fx\":\"\",\"pos\":\"\",\"peak\":" + peak + ",\"clipped\":" + clip + "}";
        String j = Report.json(x, ch.fs, an, rec, "computer");
        Files.write(new File(a[1]).toPath(), j.getBytes("UTF-8"));
        System.out.printf("%s: %.1f s at %d Hz, reliable %b%s, flag %d, %d kB, %.0f ms%n", f.getName(), x.length / ch.fs, r[0], an.reliable, an.reliable ? String.format(", rate %.0f", an.rate) : " (" + an.why + ")", Screen.flag(an), j.length() / 1024, (System.nanoTime() - t) / 1e6);
    }
}
