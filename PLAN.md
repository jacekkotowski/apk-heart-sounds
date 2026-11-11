# Heart Sounds plan

Last updated: 2026-11-11

## Status

| Part | State |
|---|---|
| Record raw WAV, countdown, timed stop, screen-off recording | Built, not yet run on a phone |
| Unprocessed source, bottom microphone, effects switched off, fallbacks | Built, not yet run on a phone |
| Play, seek, rename, delete, share, save raw WAV | Built, not yet run on a phone |
| Band-pass 20–200 and 20–600 Hz, hum filter | Frequency response measured on a computer |
| Noise reduction | Measured on synthetic beats with added noise, not on real heart sounds |
| Smooth trace and waveform graph | Tested on synthetic beats and in a headless browser |
| Filtered WAV and graph CSV export | File contents tested on a computer |
| Signal quality label | Tested on synthetic beats only |
| Heart rate and S1/S2 detection | Not started |
| Smaller files (see below) | Not started |
| Screening for unusual sounds | Not started; only after the steps above are confirmed on real recordings |

## Measured on a computer (tests/run.sh)

- Heart preset: within 0.4 dB from 30 to 150 Hz, -3 dB at 20 and 200 Hz, -48 dB at 5 Hz, -64 dB at 1 kHz.
- Aliases from reducing 48 kHz to 4 kHz: below -76 dB in the pass band.
- Medium noise reduction, light noise: in-band signal-to-noise 11.0 to 16.5 dB, gaps between beats 9.6 dB quieter, beats keep 96% of their size.
- Medium noise reduction, noise as loud as the beats: signal-to-noise up by 6 to 7 dB, but beats shrink to 64–77% of their size.
- 30 s renders in about 0.2 s on a computer.

The beats in these tests are synthetic bursts, not real heart sounds.

## Next step 1: phone test

Run the checklist in README.md. Send back one raw WAV from the chest: it shows the microphone's real low-frequency response and noise, and settles the filter band, noise reduction strength and default source.

## Next step 2: heart rate

Find beats from the smooth trace, pair S1 and S2 so each beat counts once, and show a rate only when the beats repeat regularly; otherwise "Unable to estimate reliably". Benchmark against annotated PhysioNet/CinC 2016 recordings before showing it.

## Next step 3: smaller files

Raw recordings are 5.8 MB per minute. No standard codec for heart sounds exists; published methods are research only (docs/research.md). Options, in order of effort:

1. Keep the filtered signal at 4 kHz or 2 kHz, 16-bit: 0.48 or 0.24 MB per minute, 12 to 24 times smaller, no extra loss inside the band.
2. FLAC on top, using Android's built-in encoder: lossless, expected roughly half again. Not measured.
3. A wavelet or beat-template coder as in the papers: 20 times or more beyond that on clean recordings, lossy, and must be checked not to erase murmurs.

Codec 2 and other speech codecs are unsuitable: they model a voice, not a heart.
