package pl.heartsounds;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.MicrophoneInfo;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/** Records the raw microphone signal to a WAV file in a foreground service, so it continues with the screen off. */
public class RecService extends Service {
    static final int MAX_SECONDS = 300;
    static volatile boolean running, clipped;
    static volatile int countdown;
    static volatile float level;
    static volatile long samples, clipUntil;
    static volatile int rate = 48000, limitSec;
    static volatile String file, error, notice, info = "", quality = "";
    private static final String CH = "rec";

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent in, int flags, int id) {
        if (in != null && "stop".equals(in.getAction())) { running = false; return START_NOT_STICKY; }
        if (in == null || running) return START_NOT_STICKY;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH, "Recording", NotificationManager.IMPORTANCE_LOW));
        PendingIntent ps = PendingIntent.getService(this, 1, new Intent(this, RecService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CH).setContentTitle("Recording").setContentText("Heart Sounds is recording")
                .setSmallIcon(getResources().getIdentifier("ic", "drawable", getPackageName())).setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Stop", ps).build()).build();
        try {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } catch (Exception e) { error = "Can't record in the background: " + e.getMessage(); stopSelf(); return START_NOT_STICKY; }
        final int source = in.getIntExtra("source", MediaRecorder.AudioSource.UNPROCESSED), device = in.getIntExtra("device", 0), dur = in.getIntExtra("dur", 30), delay = in.getIntExtra("delay", 3);
        final Cfg cfg = new Cfg(in.getDoubleExtra("lo", 20), in.getDoubleExtra("hi", 200), in.getDoubleExtra("hum", 0), in.getDoubleExtra("floor", 0.2));
        final String path = in.getStringExtra("path"), pos = in.getStringExtra("pos");
        file = path; error = null; running = true; samples = 0; level = 0; clipped = false; quality = ""; info = "";
        limitSec = dur <= 0 ? MAX_SECONDS : Math.min(dur, MAX_SECONDS); countdown = delay;
        new Thread(() -> loop(path, source, device, delay, cfg, pos), "rec").start();
        return START_NOT_STICKY;
    }

    static boolean unprocessedSupported(Context c) {
        return "true".equals(c.getSystemService(AudioManager.class).getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED));
    }
    static String sourceName(int s) {
        return s == MediaRecorder.AudioSource.UNPROCESSED ? "unprocessed" : s == MediaRecorder.AudioSource.VOICE_RECOGNITION ? "voice recognition" : s == MediaRecorder.AudioSource.MIC ? "microphone" : s == MediaRecorder.AudioSource.CAMCORDER ? "camcorder" : "source " + s;
    }
    /** The phone's bottom microphone, when Android names it. Many phones do not, and then Android picks the microphone. */
    static AudioDeviceInfo bottomMic(Context c) {
        for (AudioDeviceInfo d : c.getSystemService(AudioManager.class).getDevices(AudioManager.GET_DEVICES_INPUTS))
            if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC && d.getAddress() != null && d.getAddress().toLowerCase(java.util.Locale.ROOT).contains("bottom")) return d;
        return null;
    }
    private void buzz(long... ms) {
        try {
            Vibrator v = getSystemService(Vibrator.class);
            if (v != null) v.vibrate(ms.length == 1 ? VibrationEffect.createOneShot(ms[0], VibrationEffect.DEFAULT_AMPLITUDE) : VibrationEffect.createWaveform(ms, -1));
        } catch (Exception ignored) {}
    }

    private void loop(String path, int source, int device, int delay, Cfg cfg, String pos) {
        AudioRecord rec = null; OutputStream out = null; File f = new File(path); int r = 0; String mSource = "", mMic = "", mFx = "";
        try {
            // Unprocessed first, then sources that Android also leaves mostly untouched. 48 kHz first, 44.1 kHz as fallback.
            int[] sources = {source, MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC}; int used = -1;
            for (int s = 0; s < sources.length && rec == null; s++) {
                if (s > 0 && sources[s] == source) continue;
                if (sources[s] == MediaRecorder.AudioSource.UNPROCESSED && !unprocessedSupported(this)) continue;
                for (int tryRate : new int[]{48000, 44100}) {
                    int min = AudioRecord.getMinBufferSize(tryRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    if (min <= 0) continue;
                    AudioRecord a = null;
                    try { a = new AudioRecord(sources[s], tryRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, tryRate)); } catch (Exception ignored) {}
                    if (a != null && a.getState() == AudioRecord.STATE_INITIALIZED) { rec = a; r = tryRate; used = sources[s]; break; }
                    if (a != null) a.release();
                }
            }
            if (rec == null) throw new IllegalStateException("the microphone did not start");
            rate = r;
            // Switch off any automatic gain, noise suppression and echo cancelling attached to this recording.
            StringBuilder fx = new StringBuilder(); int sid = rec.getAudioSessionId();
            try { if (AutomaticGainControl.isAvailable()) { AutomaticGainControl e = AutomaticGainControl.create(sid); if (e != null) { if (e.getEnabled()) { e.setEnabled(false); fx.append(" AGC off"); } e.release(); } } } catch (Exception ignored) {}
            try { if (NoiseSuppressor.isAvailable()) { NoiseSuppressor e = NoiseSuppressor.create(sid); if (e != null) { if (e.getEnabled()) { e.setEnabled(false); fx.append(" NS off"); } e.release(); } } } catch (Exception ignored) {}
            try { if (AcousticEchoCanceler.isAvailable()) { AcousticEchoCanceler e = AcousticEchoCanceler.create(sid); if (e != null) { if (e.getEnabled()) { e.setEnabled(false); fx.append(" AEC off"); } e.release(); } } } catch (Exception ignored) {}
            AudioDeviceInfo want = null;
            if (device == 0) want = bottomMic(this);
            else for (AudioDeviceInfo d : getSystemService(AudioManager.class).getDevices(AudioManager.GET_DEVICES_INPUTS)) if (d.getId() == device) want = d;
            if (want != null) rec.setPreferredDevice(want);
            rec.startRecording();
            StringBuilder mics = new StringBuilder();
            try { for (MicrophoneInfo m : rec.getActiveMicrophones()) { String ad = m.getAddress(); mics.append(mics.length() > 0 ? "+" : "").append(ad == null || ad.isEmpty() ? m.getDescription() : ad); } } catch (Exception ignored) {}
            AudioDeviceInfo rd = rec.getRoutedDevice();
            info = (r / 1000.0) + " kHz · " + sourceName(used) + (used != source ? " (fallback)" : "") + (mics.length() > 0 ? " · mic " + mics : rd != null ? " · " + MainActivity.micName(rd) : "") + fx;
            mSource = sourceName(used) + (used != source ? " (fallback)" : ""); mMic = mics.length() > 0 ? mics.toString() : rd != null ? String.valueOf(MainActivity.micName(rd)) : ""; mFx = fx.toString().trim();
            Log.i("HeartSounds", "recording: " + info + ", requested source " + sourceName(source) + ", preferred device " + (want == null ? "none" : MainActivity.micName(want)));

            HeartChain chain = new HeartChain(r, cfg); Quality q = new Quality(chain.fs); Envelope env = new Envelope(chain.fs);
            Scope.reset((int) Math.round(chain.fs / 100));
            int n = r / 50; short[] pcm = new short[n]; byte[] bytes = new byte[n * 2];
            // Countdown: the microphone is already open so it can settle, but nothing is kept. One buzz marks the start.
            long skip = (long) delay * r, settle = delay > 0 ? r / 2 : 0, seen = 0; boolean buzzed = delay <= 0;
            long limit = (long) limitSec * r, silent = 0; boolean warned = false, clipWarned = false; int sinceFlush = 0;
            while (running && samples < limit) {
                int got = 0;
                while (got < n && running) { int k = rec.read(pcm, got, n - got); if (k < 0) throw new IllegalStateException("read error " + k); got += k; }
                if (got < n) break;
                seen += n;
                if (seen <= skip + settle) {
                    countdown = (int) Math.max(seen <= skip ? 1 : 0, Math.ceil((skip - seen) / (double) r));
                    if (!buzzed && seen >= skip) { buzzed = true; buzz(120); }
                    continue;
                }
                countdown = 0;
                if (out == null) { out = new BufferedOutputStream(new FileOutputStream(f), 1 << 16); out.write(Wav.header(r, 0)); }
                int pk = 0;
                for (int i = 0; i < n; i++) {
                    short v = pcm[i]; int a = Math.abs((int) v); if (a > pk) pk = a;
                    bytes[2 * i] = (byte) v; bytes[2 * i + 1] = (byte) (v >> 8);
                    if (chain.put(v)) { Scope.push(chain.out, env.put(chain.out)); q.push(chain.pre); }
                }
                out.write(bytes); samples += n;
                if (++sinceFlush >= 50) { out.flush(); sinceFlush = 0; quality = cfg.raw() ? "" : q.label(); }
                level = (float) Math.max(0, Math.min(1, 1 + 20 * Math.log10(Math.max(pk, 1) / 32767.0) / 60));
                if (pk >= 32000) { clipUntil = System.currentTimeMillis() + 1000; clipped = true; if (!clipWarned) { clipWarned = true; notice = "The microphone is overloading. Press more gently, or try another audio source under AUDIO."; } }
                if (pk <= 1) { silent += n; if (!warned && silent > 3L * r) { warned = true; notice = "No sound from the microphone. A call or another app may be using it."; } } else silent = 0;
            }
        } catch (Exception e) {
            error = "Recording stopped: " + e.getMessage();
        } finally {
            if (rec != null) { try { rec.stop(); } catch (Exception ignored) {} rec.release(); }
            try { if (out != null) { out.close(); Wav.finish(f, r); } } catch (Exception e) { error = "Can't finish the file: " + e.getMessage(); }
            if (samples == 0) f.delete(); else {
                buzz(0, 120, 120, 120);
                // What is known about this recording goes in a small file next to it, for the report.
                MainActivity.putMeta(f, "date", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(new java.util.Date(f.lastModified() - samples * 1000L / Math.max(r, 1))),
                        "rate", String.valueOf(r), "source", mSource, "mic", mMic, "fx", mFx, "pos", pos == null ? "" : pos);
            }
            running = false; level = 0; countdown = 0;
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        }
    }
}
