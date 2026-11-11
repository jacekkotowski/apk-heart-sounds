# Heart Sounds

Hand-built Android APK, no Gradle and no NDK: aapt2, javac, d8, zipalign, apksigner. Package `pl.heartsounds`. WebView UI in `app/assets/index.html`, signal processing and analysis in `app/src/pl/heartsounds/`. Interface text is English. Status and roadmap in `PLAN.md`, test results for the analysis in `docs/validation.md`.

Records raw 48 kHz WAV from the phone's microphone on the chest. Band-pass, noise reduction, graph and analysis are applied for display, playback and export only; the saved recording stays raw. It uses the UNPROCESSED audio source, no auto level and no gate.

Rules:

- Never commit `*.keystore`, `signing.env`, or anyone's heart recordings. The signing key lives only in the private `apk-signing-keys` repository, folder `apk-heart-sounds`.
- Never show anything as an ECG, a diagnosis, a named disease, or a heart rate that was not measured. The murmur-like-sound flag is an acoustic flag with its caveat.
- The flag limit (`Screen.SYS_DB`) was chosen on CirCor data. Re-run the evaluation in `docs/validation.md` before changing it or `Analysis.java`.
- Run `bash tests/run.sh` and `node tests/ui.js` before handing over a build. State plainly what was not tested on a phone.
- Raise `versionCode` and the version name with each delivered build.
