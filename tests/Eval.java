package pl.heartsounds;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/** Runs the analysis on a folder of annotated recordings (CirCor layout: ID_LOC.wav + ID_LOC.tsv) and prints one CSV row each. */
public class Eval {
    static float[] wav(File f, int[] rate) throws Exception {
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(f.toPath())).order(ByteOrder.LITTLE_ENDIAN); int pos = 12;
        while (pos + 8 <= b.capacity()) {
            int id = b.getInt(pos), len = b.getInt(pos + 4);
            if (id == 0x20746d66) rate[0] = b.getInt(pos + 12);
            if (id == 0x61746164) { len = Math.min(len, b.capacity() - pos - 8); float[] x = new float[len / 2]; for (int i = 0; i < x.length; i++) x[i] = b.getShort(pos + 8 + 2 * i); return x; }
            pos += 8 + len + (len & 1);
        }
        throw new IllegalStateException("no data in " + f);
    }
    public static void main(String[] a) throws Exception {
        File[] fs = new File(a[0]).listFiles((d, n) -> n.endsWith(".wav")); java.util.Arrays.sort(fs);
        System.out.println("file,reliable,rate,trueRate,s1s2,trueS1s2,segHit,beats,found,spread,strength,sysDb,sysShare,diaDb,flag");
        for (File f : fs) {
            int[] r = {0}; float[] x = wav(f, r);
            Analysis an;
            try { an = Analysis.run(x, r[0]); } catch (RuntimeException e) { System.out.println(f.getName().replace(".wav", "") + ",CRASH " + e); continue; }
            File tsv = new File(f.getPath().replace(".wav", ".tsv")); double trueRate = 0, trueGap = 0, hit = -1;
            if (tsv.exists()) {
                List<String> ls = Files.readAllLines(tsv.toPath()); java.util.ArrayList<double[]> s1 = new java.util.ArrayList<>(), s2 = new java.util.ArrayList<>(); double lo = 1e9, hi = 0;
                for (String l : ls) { String[] p = l.trim().split("\\s+"); if (p.length < 3) continue; double st = Double.parseDouble(p[0]), en = Double.parseDouble(p[1]); int s = (int) Double.parseDouble(p[2]);
                    if (s == 1) s1.add(new double[]{st, en}); if (s == 3) s2.add(new double[]{st, en}); if (s != 0) { lo = Math.min(lo, st); hi = Math.max(hi, en); } }
                java.util.ArrayList<Double> iv = new java.util.ArrayList<>(), gp = new java.util.ArrayList<>();
                for (int i = 0; i + 1 < s1.size(); i++) { double d = s1.get(i + 1)[0] - s1.get(i)[0]; if (d < 2) iv.add(d); }
                for (double[] q : s1) for (double[] w : s2) if (w[0] > q[0] && w[0] - q[0] < 0.6) { gp.add((w[0] + w[1]) / 2 - (q[0] + q[1]) / 2); break; }
                java.util.Collections.sort(iv); java.util.Collections.sort(gp);
                if (!iv.isEmpty()) trueRate = 60 / iv.get(iv.size() / 2); if (!gp.isEmpty()) trueGap = gp.get(gp.size() / 2);
                int in = 0, tot = 0; // the 20 Hz loudness filter delays our marks by about 25 ms
                for (double t : an.s1) { if (t < lo || t > hi) continue; tot++; for (double[] q : s1) if (t - 0.025 >= q[0] - 0.06 && t - 0.025 <= q[1] + 0.06) { in++; break; } }
                if (tot > 0) hit = in / (double) tot;
            }
            System.out.printf(Locale.ROOT, "%s,%b,%.1f,%.1f,%.3f,%.3f,%.2f,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d%n", f.getName().replace(".wav", ""), an.reliable, an.rate, trueRate, an.s1s2, trueGap, hit, an.beats, an.found, an.spread, an.strength, an.sysDb, an.sysShare, an.diaDb, Screen.flag(an));
        }
    }
}
