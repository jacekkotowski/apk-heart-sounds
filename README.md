# Heart Sounds

Offline Android recorder for heart sounds picked up by the phone's own microphone pressed against the chest. It records the raw signal, filters it to the heart-sound band, reduces steady noise and draws the result as a smooth trace or a waveform. It started inside the Codec2 Recorder project and reuses that app's screen style, hand build (no Gradle) and filter approach, but shares no code with it.

Experimental. Not a medical device and not an ECG: the graph shows sound loudness, not the heart's electrical activity. It cannot diagnose or rule out heart disease.

Current version: 0.3.0. Roadmap and test status in [PLAN.md](PLAN.md), background in [docs/research.md](docs/research.md), test results for the analysis in [docs/validation.md](docs/validation.md).

## What it does

- Records 16-bit mono at 48 kHz (44.1 kHz as fallback) to a WAV file. The saved file is always the raw microphone signal.
- Asks Android for the unprocessed audio source and the bottom microphone, falls back when the phone lacks them, and shows what it actually got while recording.
- 3-second countdown, a buzz at the start and a double buzz at the end, fixed length of 15, 30 or 60 s or until STOP (5 min at most). Continues with the screen off.
- Filters: Heart sounds (20–200 Hz), Wide (20–600 Hz), Raw. Optional 50/60 Hz hum filter. Noise reduction: off, gentle, medium, strong.
- Graph of the last 6 seconds: smooth trace on an ECG-paper-style grid, or waveform.
- Play, seek, rename, delete, share. Save raw WAV, filtered WAV, and graph CSV (time, filtered signal, smooth trace) to Download/HeartSounds.
- ANALYSE on a recording: finds the beats and shows a typical beat (all beats lined up and averaged), heart rate, the gap between the two heart sounds, a beat-by-beat strip with unusual beats marked, and an experimental flag for a murmur-like sound between the two heart sounds. When beats are not found reliably it says so and gives no number.
- REPORT on a recording: one scrolling page in three levels (at a glance, clinical view, data view) with a short primer, drawn from the same analysis. It shows the typical beat, a sound trace of the cleanest 10 seconds, the rhythm, timing and gap levels, spectrogram, spectrum, every beat in a table, and what the numbers do and do not mean. Print or save it as an A4 PDF, share the PDF, save the per-beat CSV and the full JSON to Download/HeartSounds. A recording without reliable beats still gets a report that says so.
- Chest position: tap one of four spots on a small chest before recording, or in the report afterwards. It is stored in a small `.json` file next to the recording, together with the date, sample rate, audio source and microphone.
- Playback pitch: true, or raised by 250 Hz so the beats can be heard on the phone speaker. Exports always keep the true pitch.
- No internet permission.

## Signal path

`microphone 48 kHz → low-pass and reduce to 4 kHz → 4th-order Butterworth band-pass → optional hum notch → noise reduction → graph, playback, exports`

- No automatic level, gate or compressor while recording, so beats keep their relative size. Playback and the filtered WAV use one gain for the whole recording plus a look-ahead limiter.
- Noise reduction treats the quietest level in each frequency bin over the last 3 seconds as noise and lowers each bin towards it. It removes steady noise. It does not remove speech, rubbing or knocks, and a sound that never pauses would be treated as noise.
- Smooth trace: size of the filtered signal, low-passed at 15 Hz.

## Build

Needs: Android SDK build-tools 36.0.0 and platforms;android-36, JDK 17 or newer, zip. No NDK and no Gradle. Tested on Linux only.

Copy `heartsounds.keystore` and `signing.env` from the `apk-heart-sounds` folder of the private `apk-signing-keys` repository into the project folder. Neither file is in git here. Builds up to 0.2 were signed with temporary keys, so uninstall them before installing 0.2.1 or later; from 0.2.1 on, new versions install over old ones.

```
export ANDROID_SDK_ROOT=...
bash build.sh        # writes HeartSounds.apk and prints its SHA-256
```

## Tests

- `bash tests/run.sh`: filter frequency response, alias rejection, noise reduction on synthetic beats, level setting, WAV and CSV files. Needs only a JDK.
- `node tests/ui.js [folder]`: loads the screen in a headless browser with a stand-in for the Android side and saves screenshots. Needs Playwright for Node.
- `node tests/report.js [folder] [report.json ...]`: renders the report from the synthetic JSON that `tests/run.sh` writes, at phone width and as an A4 PDF; fails on script errors, sideways overflow, a missing caveat, a disease name, or more than 4 pages. To render a real recording: `java -cp out/test pl.heartsounds.MakeReport recording.wav r.json`, then pass `r.json` as an extra argument. Do not commit either file.

## Known limits

- Version 0.1 recorded real heart sounds on the owner's phone. Everything added in 0.2 and 0.3 has not been run on a phone. See PLAN.md.
- The report was rendered from synthetic beats and from stethoscope recordings, not yet from a phone recording. Printing, Share PDF and saving from the report use Android services that were not run here. Share PDF relies on a part of Android that some phones may refuse; it then opens the print screen instead.
- Many phone microphones roll off below about 100 Hz, and some phones process the sound even on the unprocessed source. Whether the 20–200 Hz band is usable depends on the phone.
- Android does not always say which microphone is in use; the bottom one is requested only when the phone names it.
- Phone speakers can't reproduce these frequencies. Use headphones.
- Signal quality compares loud and quiet moments. It does not check that the sound is a heart.
- The murmur-like-sound flag was tested only on stethoscope recordings of children, where it found 59% of murmurs and wrongly flagged 4% of recordings without one. It has not been tested on phone recordings or adults. It cannot name or rule out a disease.
- Beat finding assumes the gap from the first to the second heart sound is the shorter one. At high heart rates that can fail and the two sounds get swapped.
- Unusual-beat marks are not validated; they mostly show knocks and movement.

## Check on the phone

1. Install, open, press REC: Android asks for the microphone only now.
2. Countdown, one buzz, 30 s recording, double buzz, recording appears in the list.
3. The line under the clock names the sample rate, audio source and microphone.
4. Bottom edge just left of the breastbone, between the ribs: does the smooth trace show two humps per beat? Try Heart sounds, Wide and Raw.
5. PLAY with headphones in each filter. SAVE RAW WAV, SAVE FILTERED WAV and SAVE GRAPH CSV appear in Download/HeartSounds.
6. Record with the screen switched off after pressing REC.
7. ANALYSE: typical beat with S1 and S2, a heart rate, and the flag line. Compare the rate with a pulse count.
8. AUDIO > Playback pitch > Raised 250 Hz, then PLAY on the phone speaker.
9. Tap a spot on the small chest, record, then REPORT: the page opens within 3 seconds for 60 s, shows the position, and the Back button and Android's back gesture return to the recordings.
10. In the report: Report as PDF (print screen, Save as PDF, at most 4 pages with margins), Share PDF, Per-beat CSV and Everything as JSON (both appear in Download/HeartSounds).
11. Flight mode: everything still works.

## Licences

The look-ahead limiter and screen style come from Codec2 Recorder, which builds on Codec2Recorder by tschudin (MIT). No third-party code is included. Test data from CirCor DigiScope (Open Data Commons Attribution License v1.0) is not in this repo.
