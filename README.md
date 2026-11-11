# Heart Sounds

Offline Android recorder for heart sounds picked up by the phone's own microphone pressed against the chest. It records the raw signal, filters it to the heart-sound band, reduces steady noise and draws the result as a smooth trace or a waveform. A second app in this repo; it reuses Codec2 Recorder's screen layout, hand build and filter approach, and shares no code with it at build time.

Experimental. Not a medical device and not an ECG: the graph shows sound loudness, not the heart's electrical activity. It cannot diagnose or rule out heart disease.

Current version: 0.2. Roadmap and test status in [PLAN.md](PLAN.md), background in [docs/research.md](docs/research.md), test results for the analysis in [docs/validation.md](docs/validation.md).

## What it does

- Records 16-bit mono at 48 kHz (44.1 kHz as fallback) to a WAV file. The saved file is always the raw microphone signal.
- Asks Android for the unprocessed audio source and the bottom microphone, falls back when the phone lacks them, and shows what it actually got while recording.
- 3-second countdown, a buzz at the start and a double buzz at the end, fixed length of 15, 30 or 60 s or until STOP (5 min at most). Continues with the screen off.
- Filters: Heart sounds (20–200 Hz), Wide (20–600 Hz), Raw. Optional 50/60 Hz hum filter. Noise reduction: off, gentle, medium, strong.
- Graph of the last 6 seconds: smooth trace on an ECG-paper-style grid, or waveform.
- Play, seek, rename, delete, share. Save raw WAV, filtered WAV, and graph CSV (time, filtered signal, smooth trace) to Download/HeartSounds.
- ANALYSE on a recording: finds the beats and shows a typical beat (all beats lined up and averaged), heart rate, the gap between the two heart sounds, a beat-by-beat strip with unusual beats marked, and an experimental flag for a murmur-like sound between the two heart sounds. When beats are not found reliably it says so and gives no number.
- Playback pitch: true, or raised by 250 Hz so the beats can be heard on the phone speaker. Exports always keep the true pitch.
- No internet permission.

## Signal path

`microphone 48 kHz → low-pass and reduce to 4 kHz → 4th-order Butterworth band-pass → optional hum notch → noise reduction → graph, playback, exports`

- No automatic level, gate or compressor while recording, so beats keep their relative size. Playback and the filtered WAV use one gain for the whole recording plus a look-ahead limiter.
- Noise reduction treats the quietest level in each frequency bin over the last 3 seconds as noise and lowers each bin towards it. It removes steady noise. It does not remove speech, rubbing or knocks, and a sound that never pauses would be treated as noise.
- Smooth trace: size of the filtered signal, low-passed at 15 Hz.

## Build

Needs the same tools as Codec2 Recorder except the NDK: Android SDK build-tools 36.0.0 and platforms;android-36, JDK 17 or newer, zip. Uses `../signing.env` and the keystore it names.

```
export ANDROID_SDK_ROOT=...
bash heart/build.sh        # writes heart/HeartSounds.apk and prints its SHA-256
```

## Tests

- `bash heart/tests/run.sh`: filter frequency response, alias rejection, noise reduction on synthetic beats, level setting, WAV and CSV files. Needs only a JDK.
- `node heart/tests/ui.js [folder]`: loads the screen in a headless browser with a stand-in for the Android side and saves screenshots. Needs Playwright for Node.

## Known limits

- Version 0.1 recorded real heart sounds on the owner's phone. Everything added in 0.2 has not been run on a phone. See PLAN.md.
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
9. Flight mode: everything still works.
