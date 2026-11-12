package pl.heartsounds;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_PERM = 1;
    private static final int BG = Color.rgb(27, 36, 48);
    private WebView web;
    private volatile int source = MediaRecorder.AudioSource.UNPROCESSED, device = 0, durSec = 30;
    private volatile double shiftHz = 0;
    private volatile Cfg cfg = new Cfg(20, 200, 0, 0.2);
    private boolean pending;
    private volatile Thread player;
    private volatile boolean playStop, busy;
    private volatile String playName, notice;
    private volatile double playPos, playDur, seekTo = -1;
    private volatile String analysis, report, nextPos = "";
    private static final String POSITIONS = " ur ul ll apex ";

    static String micName(AudioDeviceInfo d) {
        int t = d.getType(); String kind;
        if (t == AudioDeviceInfo.TYPE_BUILTIN_MIC) kind = "Phone microphone";
        else if (t == AudioDeviceInfo.TYPE_USB_DEVICE || t == AudioDeviceInfo.TYPE_USB_HEADSET) kind = "USB";
        else if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET) kind = "Wired headset";
        else return null;
        String addr = d.getAddress() == null ? "" : d.getAddress();
        return kind + (addr.isEmpty() ? "" : " (" + addr + ")") + " #" + d.getId();
    }

    static File dir(Context c) { File d = new File(c.getExternalFilesDir(null), "recordings"); d.mkdirs(); return d; }
    private static String jq(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    private File named(String name) { return new File(dir(this), new File(name).getName()); }
    static File reportDir(Context c) { File d = new File(c.getCacheDir(), "report"); d.mkdirs(); return d; }

    /** Small JSON file next to a recording: date, sample rate, audio source, microphone, chest position. */
    static File side(File wav) { return new File(wav.getPath() + ".json"); }
    static org.json.JSONObject meta(File wav) {
        try { byte[] b = java.nio.file.Files.readAllBytes(side(wav).toPath()); return new org.json.JSONObject(new String(b, "UTF-8")); } catch (Exception e) { return new org.json.JSONObject(); }
    }
    static synchronized void putMeta(File wav, String... kv) {
        try {
            org.json.JSONObject o = meta(wav); for (int i = 0; i + 1 < kv.length; i += 2) o.put(kv[i], kv[i + 1]);
            java.nio.file.Files.write(side(wav).toPath(), o.toString().getBytes("UTF-8"));
        } catch (Exception ignored) {}
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        web = new WebView(this);
        web.setBackgroundColor(BG);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setBlockNetworkLoads(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.addJavascriptInterface(new ReportHost(), "ReportHost");
        // A WebView ignores its own padding, so the system-bar insets go on a parent layout.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((View v, WindowInsets i) -> {
                android.graphics.Insets p = i.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
                v.setPadding(p.left, p.top, p.right, p.bottom);
                return WindowInsets.CONSUMED;
            });
        } else root.setFitsSystemWindows(true);
        setContentView(root);
        web.loadUrl("file:///android_asset/index.html");
    }

    private void startRecording() {
        stopPlayback();
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date());
        Cfg c = cfg;
        Intent i = new Intent(this, RecService.class).putExtra("source", source).putExtra("device", device).putExtra("dur", durSec).putExtra("delay", 3)
                .putExtra("pos", nextPos).putExtra("lo", c.lo).putExtra("hi", c.hi).putExtra("hum", c.hum).putExtra("floor", c.floor).putExtra("path", new File(dir(this), stamp + ".wav").getPath());
        try { startForegroundService(i); } catch (Exception e) { notice = "Can't start recording: " + e.getMessage(); }
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code != REQ_PERM) return;
        for (int k = 0; k < perms.length; k++) {
            if (Manifest.permission.RECORD_AUDIO.equals(perms[k]) && res[k] != PackageManager.PERMISSION_GRANTED) {
                notice = "Recording needs the microphone. Allow it in Android Settings > Apps > Heart Sounds > Permissions.";
                pending = false; return;
            }
        }
        if (pending) { pending = false; startRecording(); }
    }

    private void stopPlayback() {
        playStop = true; Thread t = player;
        if (t != null) { try { t.join(1500); } catch (InterruptedException ignored) {} }
        player = null;
    }

    /** Plays a recording through the current filter. The filtered copy is made first, then streamed, and the waveform follows it. */
    private void play(String name) {
        stopPlayback();
        if (RecService.running) return;
        final File f = named(name), tmp = new File(getCacheDir(), "play.wav"); final Cfg c = cfg; final double shift = shiftHz;
        playStop = false; playName = name; playPos = 0; playDur = 0; seekTo = -1;
        player = new Thread(() -> {
            AudioTrack tr = null;
            try {
                busy = true; try { Render.wav(f, tmp, c, shift); } finally { busy = false; }
                try (RandomAccessFile in = new RandomAccessFile(tmp, "r")) {
                    int rate = Wav.rate(tmp); long total = Wav.samples(tmp);
                    if (rate < 0 || total == 0) throw new IllegalStateException("the recording is empty");
                    playDur = total / (double) rate;
                    int min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    tr = new AudioTrack.Builder()
                            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                            .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                            .setBufferSizeInBytes(Math.max(min, rate / 5 * 2)).setTransferMode(AudioTrack.MODE_STREAM).build();
                    tr.play();
                    Scope.reset(Math.max(1, rate / 100)); Envelope env = new Envelope(rate);
                    int n = rate / 20; byte[] b = new byte[n * 2]; short[] pcm = new short[n]; long pos = 0;
                    in.seek(Wav.HEADER);
                    while (!playStop) {
                        double s = seekTo; if (s >= 0) { seekTo = -1; pos = (long) (s * total); in.seek(Wav.HEADER + 2 * pos); }
                        int got = in.read(b); if (got < 2) break;
                        int k = got / 2;
                        for (int i = 0; i < k; i++) { pcm[i] = (short) ((b[2 * i] & 255) | (b[2 * i + 1] << 8)); Scope.push(pcm[i], env.put(pcm[i])); }
                        tr.write(pcm, 0, k);
                        pos += k; playPos = pos / (double) rate;
                    }
                    if (!playStop) { tr.stop(); Thread.sleep(300); } // let the last buffer play out
                }
            } catch (Exception e) {
                notice = "Can't play: " + e.getMessage();
            } finally {
                if (tr != null) { try { tr.pause(); tr.flush(); } catch (Exception ignored) {} tr.release(); }
                if (Thread.currentThread() == player) { player = null; playName = null; }
            }
        }, "play");
        player.start();
    }

    private void toDownloads(String display, String mime, File src) throws Exception {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, display);
        v.put(MediaStore.Downloads.MIME_TYPE, mime);
        v.put(MediaStore.Downloads.RELATIVE_PATH, "Download/HeartSounds");
        ContentResolver r = getContentResolver();
        Uri u = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (u == null) throw new IllegalStateException("Android refused to create the file");
        try (OutputStream o = r.openOutputStream(u); InputStream in = new FileInputStream(src)) {
            byte[] buf = new byte[1 << 16]; int k;
            while ((k = in.read(buf)) > 0) o.write(buf, 0, k);
        }
    }
    private interface Job { void run(File tmp) throws Exception; }
    /** Makes an export in a temporary file, then copies it to Download/HeartSounds. */
    private void export(String display, String mime, Job job) {
        new Thread(() -> {
            File tmp = new File(getCacheDir(), "export.tmp");
            try { busy = true; job.run(tmp); toDownloads(display, mime, tmp); notice = "Saved to Download/HeartSounds: " + display; }
            catch (Exception e) { notice = "Save failed: " + e.getMessage(); }
            finally { busy = false; tmp.delete(); }
        }, "export").start();
    }

    class Bridge {
        @JavascriptInterface public String status() {
            String n = notice; notice = null;
            String err = RecService.error; RecService.error = null;
            String rn = RecService.notice; RecService.notice = null;
            String msg = err != null ? err : (rn != null ? rn : n);
            boolean rec = RecService.running;
            return "{\"rec\":" + rec + ",\"count\":" + RecService.countdown + ",\"secs\":" + (rec ? RecService.samples / (double) RecService.rate : 0) + ",\"limit\":" + RecService.limitSec
                    + ",\"level\":" + RecService.level + ",\"clip\":" + (System.currentTimeMillis() < RecService.clipUntil) + ",\"quality\":" + jq(rec ? RecService.quality : "")
                    + ",\"info\":" + jq(RecService.info == null ? "" : RecService.info) + ",\"busy\":" + busy
                    + ",\"play\":" + (playName == null ? "null" : jq(playName)) + ",\"pos\":" + playPos + ",\"dur\":" + playDur
                    + ",\"msg\":" + (msg == null ? "null" : jq(msg)) + "}";
        }
        @JavascriptInterface public String wave(double since) { return Scope.since((long) since); }
        @JavascriptInterface public String list() {
            File[] fs = dir(MainActivity.this).listFiles();
            if (fs == null) return "[]";
            Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            StringBuilder s = new StringBuilder("[");
            for (File f : fs) {
                if (RecService.running && f.getPath().equals(RecService.file)) continue;
                int rate = Wav.rate(f); if (rate < 0) continue;
                if (s.length() > 1) s.append(',');
                s.append("{\"name\":").append(jq(f.getName())).append(",\"pos\":").append(jq(meta(f).optString("pos"))).append(",\"rate\":").append(rate).append(",\"secs\":").append(Wav.samples(f) / (double) rate)
                        .append(",\"bytes\":").append(f.length()).append('}');
            }
            return s.append(']').toString();
        }
        @JavascriptInterface public void startRec() {
            runOnUiThread(() -> {
                boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                boolean nt = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
                if (mic && (nt || getSharedPreferences("p", 0).getBoolean("asked", false))) { startRecording(); return; }
                pending = true;
                getSharedPreferences("p", 0).edit().putBoolean("asked", true).apply();
                requestPermissions(Build.VERSION.SDK_INT >= 33 ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS} : new String[]{Manifest.permission.RECORD_AUDIO}, REQ_PERM);
            });
        }
        @JavascriptInterface public void stopRec() { RecService.running = false; }
        @JavascriptInterface public boolean unprocessed() { return RecService.unprocessedSupported(MainActivity.this); }
        @JavascriptInterface public String microphones() {
            StringBuilder s = new StringBuilder("[");
            for (AudioDeviceInfo d : getSystemService(AudioManager.class).getDevices(AudioManager.GET_DEVICES_INPUTS)) {
                String nm = micName(d); if (nm == null) continue;
                if (s.length() > 1) s.append(',');
                s.append("{\"id\":").append(d.getId()).append(",\"name\":").append(jq(nm)).append('}');
            }
            return s.append(']').toString();
        }
        @JavascriptInterface public void config(int src, int dev, double lo, double hi, double hum, double floor, int dur, double shift) {
            shiftHz = shift; source = src; device = dev; durSec = dur; cfg = new Cfg(lo, hi, hum, floor);
        }
        /** Keeps the screen on while recording, so the timed recording can be watched. */
        @JavascriptInterface public void keepOn(boolean on) {
            runOnUiThread(() -> { if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); });
        }
        @JavascriptInterface public void play(String name) { runOnUiThread(() -> MainActivity.this.play(name)); }
        @JavascriptInterface public void stopPlay() { runOnUiThread(MainActivity.this::stopPlayback); }
        @JavascriptInterface public void seek(double fraction) { seekTo = Math.max(0, Math.min(0.999, fraction)); }
        @JavascriptInterface public void remove(String name) { if (name.equals(playName)) stopPlay(); named(name).delete(); side(named(name)).delete(); }
        /** Chest position for the next recording: ur, ul, ll, apex or empty. */
        @JavascriptInterface public void nextPos(String pos) { nextPos = POSITIONS.contains(" " + pos + " ") ? pos : ""; }
        @JavascriptInterface public boolean rename(String name, String to) {
            String clean = to.trim().replaceAll("[^\\p{L}\\p{N} _.-]", "_");
            if (clean.isEmpty()) return false;
            if (!clean.endsWith(".wav")) clean += ".wav";
            File dst = named(clean);
            if (dst.exists() || !named(name).renameTo(dst)) return false;
            side(named(name)).renameTo(side(dst)); return true;
        }
        @JavascriptInterface public void share(String name) {
            runOnUiThread(() -> {
                Uri u = new Uri.Builder().scheme("content").authority("pl.heartsounds.files").appendPath(new File(name).getName()).build();
                Intent i = new Intent(Intent.ACTION_SEND).setType("audio/wav").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Share raw recording"));
            });
        }
        @JavascriptInterface public void saveRaw(String name) {
            new Thread(() -> {
                try { toDownloads(name, "audio/wav", named(name)); notice = "Saved to Download/HeartSounds: " + name; }
                catch (Exception e) { notice = "Save failed: " + e.getMessage(); }
            }).start();
        }
        @JavascriptInterface public void saveFiltered(String name) {
            final Cfg c = cfg; if (c.raw()) { notice = "Choose Heart sounds or Wide first. Raw is saved with SAVE RAW WAV."; return; }
            export(name.replaceAll("\\.wav$", "") + "_" + c.tag() + ".wav", "audio/wav", tmp -> Render.wav(named(name), tmp, c, 0));
        }
        /** Finds the beats in a recording and measures them. The result is read with analysis(). */
        @JavascriptInterface public void analyse(String name) {
            new Thread(() -> {
                try {
                    busy = true; int[] fs = {0};
                    float[] x = Render.signal(named(name), new Cfg(0, 0, 0, 1), fs); // unfiltered, reduced to about 4 kHz; the analysis applies its own filters
                    analysis = "{\"name\":" + jq(name) + ",\"a\":" + Analysis.run(x, fs[0]).json() + "}";
                } catch (Exception e) { notice = "Can't analyse: " + e.getMessage(); analysis = "{\"name\":" + jq(name) + ",\"a\":null}"; }
                finally { busy = false; }
            }, "analyse").start();
        }
        @JavascriptInterface public String analysis() { String a = analysis; analysis = null; return a == null ? "null" : a; }
        @JavascriptInterface public void saveCsv(String name) {
            final Cfg c = cfg; if (c.raw()) { notice = "Choose Heart sounds or Wide first. The CSV holds the filtered signal."; return; }
            export(name.replaceAll("\\.wav$", "") + "_" + c.tag() + ".csv", "text/csv", tmp -> Render.csv(named(name), tmp, c));
        }
    }

    /** The report page's side of the app: data, saving, printing and the chest position. The page also works without it. */
    class ReportHost {
        @JavascriptInterface public void start(String name) {
            report = null;
            new Thread(() -> {
                try {
                    File f = named(name); int[] fs = {0}; long[] st = {0, 0};
                    float[] x = Render.signal(f, new Cfg(0, 0, 0, 1), fs, st); // unfiltered, reduced to about 4 kHz
                    org.json.JSONObject m = meta(f), rec = new org.json.JSONObject();
                    String date = m.optString("date");
                    if (date.isEmpty()) { java.util.regex.Matcher q = java.util.regex.Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})_(\\d{2})-(\\d{2})-\\d{2}").matcher(f.getName()); date = q.find() ? q.group(1) + " " + q.group(2) + ":" + q.group(3) : new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date(f.lastModified())); }
                    rec.put("name", f.getName()).put("date", date).put("rate", Wav.rate(f)).put("source", m.optString("source")).put("mic", m.optString("mic")).put("fx", m.optString("fx")).put("pos", m.optString("pos")).put("peak", st[0]).put("clipped", st[1]);
                    String ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                    report = Report.json(x, fs[0], Analysis.run(x, fs[0]), rec.toString(), ver);
                } catch (Throwable e) { report = "{\"error\":" + jq(String.valueOf(e.getMessage())) + "}"; }
            }, "report").start();
        }
        @JavascriptInterface public String poll() { String r = report; if (r != null) report = null; return r == null ? "null" : r; }
        @JavascriptInterface public String message() { String n = notice; notice = null; return n == null ? "" : n; }
        @JavascriptInterface public void setPosition(String name, String pos) { if (POSITIONS.contains(" " + pos + " ")) putMeta(named(name), "pos", pos); }
        @JavascriptInterface public void save(String file, String mime, String text) {
            final String clean = new File(file).getName().replaceAll("[^\\p{L}\\p{N} _.-]", "_");
            export(clean, mime, tmp -> java.nio.file.Files.write(tmp.toPath(), text.getBytes("UTF-8")));
        }
        /** Android's print screen, where "Save as PDF" is one of the printers. */
        @JavascriptInterface public void print(String job) {
            runOnUiThread(() -> {
                try { getSystemService(PrintManager.class).print(job, web.createPrintDocumentAdapter(job), a4()); }
                catch (Exception e) { notice = "Can't print: " + e.getMessage(); }
            });
        }
        /** Makes the PDF without the print screen and opens the share sheet. Falls back to the print screen when the phone does not allow it. */
        @JavascriptInterface public void sharePdf(String job) {
            runOnUiThread(() -> {
                try {
                    final String file = new File(job).getName().replaceAll("[^\\p{L}\\p{N} _.-]", "_") + ".pdf"; final File out = new File(reportDir(MainActivity.this), file);
                    android.print.PdfWriter.write(web.createPrintDocumentAdapter(job), a4(), out, err -> runOnUiThread(() -> {
                        if (err != null || out.length() == 0) { notice = "Can't make the PDF here" + (err != null ? " (" + err + ")" : "") + ". Use Report as PDF and choose Save as PDF."; return; }
                        Uri u = new Uri.Builder().scheme("content").authority("pl.heartsounds.files").appendPath("report").appendPath(file).build();
                        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share report"));
                    }));
                } catch (Throwable e) { notice = "Sharing a PDF directly is not possible on this phone. Choose Save as PDF on the print screen."; print(job); }
            });
        }
    }
    private static PrintAttributes a4() {
        return new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setResolution(new PrintAttributes.Resolution("pdf", "pdf", 300, 300)).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build();
    }

    @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }

    @Override protected void onDestroy() { stopPlayback(); web.destroy(); super.onDestroy(); }
}
