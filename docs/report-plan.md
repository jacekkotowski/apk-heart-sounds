# Plan: heart sound report

Written 2026-11-12 for a new session. Starting point: Heart Sounds 0.2.1 on `main`.

## Goal

One report per recording that a curious user, a doctor and a data scientist can each read without help, and that teaches as it goes. It must look good on a phone, print cleanly to A4 PDF, and work offline.

The report describes sounds. It never shows an ECG, a diagnosis or a named disease (see CLAUDE.md).

## Shape of the report

One scrolling page, three levels of depth. Each level starts with a one-line statement of what it shows; nobody has to read past their own level.

### 1. At a glance (user)

- Recording date, length, signal quality in words.
- Three large numbers: heart rate, time from first to second heart sound, share of clean beats.
- The typical beat as a labelled picture: S1, S2, the gap between them (systole) and the gap after (diastole).
- The murmur-like-sound result in one sentence, with its caveat directly under it.
- "What to do with this": record again in the same spot to compare; see a doctor for symptoms whatever the report says.

### 2. Clinical view (doctor)

- Phonocardiogram strip of the cleanest 10 seconds on a time grid with 0.2 s and 0.04 s lines, S1 and S2 marked on every beat.
- Whole-recording rhythm strip: one dot per beat, unusual beats marked with the reason.
- Typical beat with the middle-half band, in two frequency bands (40–200 Hz and 150–600 Hz) one above the other.
- Timing table: rate, beat length and its spread, S1–S2, S2–S1, their ratio, number of beats used.
- Sound level in each gap relative to the heart sounds, per band.
- Recording conditions: microphone, audio source, sample rate, clipping, noise level, where on the chest (user picks from a small chest diagram before or after recording).
- Limits box: phone microphone cuts off below about 50 Hz on the tested phone; flag validated on children's stethoscope recordings only.

### 3. Data view (data scientist)

- Spectrogram 0–600 Hz of the whole recording with beat marks.
- Average spectrum, with the microphone's roll-off visible.
- Beat-length histogram and a plot of each beat length against the next.
- All beats overlaid, lined up on S1.
- Per-beat table: time, length, S1–S2, level in each gap, unusual-beat reason.
- Method box: filters, limits, algorithm version, validation numbers with a link to `docs/validation.md`.
- Export buttons: report as PDF, per-beat CSV, everything as JSON.

## Teaching

- Every chart has a short "How to read this" note: what the axes are, what a normal picture looks like, what the chart cannot show.
- Every number has a tap-to-open line: what it means, what a typical range is where one is well established, and what it does not tell you.
- A short illustrated primer at the end: what makes the two heart sounds, what a murmur is and why many are harmless, why a phone hears less than a stethoscope, how to get a better recording.
- Plain words first, the technical term in brackets once.

## Design

- Same dark palette as the app on screen; a light, ink-saving version for print (`@media print`).
- Charts as inline SVG so they stay sharp in PDF; one accent colour for heart sounds, amber only for things that need attention.
- One column on a phone, two on A4. Page breaks between the three levels.
- Use the `dataviz` skill before drawing any chart.

## Build steps

1. **Data.** Extend `Analysis.java` to return what the report needs: per-beat table, typical beat in both bands, spectrum, a reduced spectrogram, the cleanest 10 seconds, noise level. Keep the flag logic untouched. Add tests in `tests/DspTest.java`.
2. **Report page.** New `app/assets/report.html`, drawn from one JSON object, with no Android calls inside it so it can be opened and tested in a browser. Level 1 first, then 2, then 3.
3. **In the app.** REPORT button on a recording; opens the page in the WebView. PDF through Android's print service; share PDF; save CSV and JSON to Download/HeartSounds.
4. **Chest position.** Small diagram with four tap targets, stored next to the recording and shown in the report.
5. **Teaching text.** Write the notes and the primer; keep each note under three lines.
6. **Checks.** `tests/report.js`: render the report from a fixed JSON at phone width and A4, save screenshots, fail on script errors, overflow, or missing caveat text. Then build, bump the version, hand over the APK.

Deliver a working APK after step 3; steps 4 to 6 refine it.

## Done when

- A report opens within 3 seconds for a 60 s recording on the phone.
- The PDF is at most 4 pages and readable in black and white.
- All three levels render from the owner's real recording, not only synthetic beats.
- A recording with no reliable beats still gives a report that says so and shows the signal, spectrum and advice.
- No sentence names a disease or presents the trace as an ECG.

## For the new session

- Attach `apk-heart-sounds` and `apk-signing-keys`. Copy the key files from `apk-signing-keys/apk-heart-sounds/` into the project folder before building.
- The Android SDK is not preinstalled: install command-line tools, `build-tools;36.0.0` and `platforms;android-36` from dl.google.com.
- No heart recording is in the repo. Ask the owner to upload one raw WAV again for testing; do not commit it.
- CirCor data for re-validation comes from physionet.org (allowed in the environment used so far).

## Later, not in this plan

- Comparing recordings over time.
- Polish version of the report.
- Phone-versus-stethoscope validation of the flag.
