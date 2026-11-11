# Heart Sounds plan

Last updated: 2026-11-11

## Status

| Part | State |
|---|---|
| Record raw WAV, timed stop | Worked on the owner's phone in 0.1: a 30 s recording with clear heart sounds |
| Countdown, buzzes, screen-off recording | Built, not confirmed on a phone |
| Unprocessed source, bottom microphone, effects switched off, fallbacks | Built, not yet run on a phone |
| Play, seek, rename, delete, share, save raw WAV | Built, not yet run on a phone |
| Band-pass 20–200 and 20–600 Hz, hum filter | Frequency response measured on a computer |
| Noise reduction | Measured on synthetic beats with added noise, not on real heart sounds |
| Smooth trace and waveform graph | Tested on synthetic beats and in a headless browser |
| Filtered WAV and graph CSV export | File contents tested on a computer |
| Signal quality label | Tested on synthetic beats only |
| Beat finding, heart rate, typical beat (ANALYSE) | Tested on 1,749 stethoscope recordings and one phone recording, see docs/validation.md. Not run on a phone |
| Murmur-like-sound flag | Tested on stethoscope recordings of children only: 59% of murmurs flagged, 4% false flags. Not tested on phones or adults |
| Unusual-beat marks | Built, not validated |
| Raised-pitch playback | Tested on a computer, not heard on a phone |
| Smaller files (see below) | Not started |
| Naming any disease | Not built and not planned: no data supports it |

## Measured on a computer (tests/run.sh)

- Heart preset: within 0.4 dB from 30 to 150 Hz, -3 dB at 20 and 200 Hz, -48 dB at 5 Hz, -64 dB at 1 kHz.
- Aliases from reducing 48 kHz to 4 kHz: below -76 dB in the pass band.
- Medium noise reduction, light noise: in-band signal-to-noise 11.0 to 16.5 dB, gaps between beats 9.6 dB quieter, beats keep 96% of their size.
- Medium noise reduction, noise as loud as the beats: signal-to-noise up by 6 to 7 dB, but beats shrink to 64–77% of their size.
- 30 s renders in about 0.2 s on a computer.

The beats in these tests are synthetic bursts, not real heart sounds.

## Found on the owner's phone (one 30 s recording, 2026-11-11)

- Unprocessed source at 48 kHz, no clipping, heart sounds at about 100 to 300 of 32767.
- The microphone cuts off steeply below about 50 Hz; most energy is at 60 to 150 Hz. The 20 Hz filter edge is harmless but the usable band starts near 50 Hz.
- Analysis: 81 beats per minute, first to second sound 0.31 s, 34 clean beats, no flag. No reference measurement was taken.

## Next step 1: phone test of 0.2

Run the checklist in README.md, and count the pulse during one recording to check the heart rate.

## Next step 2: phone validation of the flag

See the last section of docs/validation.md. Until then the flag stays labelled experimental.

## Next step 3: smaller files

Raw recordings are 5.8 MB per minute. No standard codec for heart sounds exists; published methods are research only (docs/research.md). Options, in order of effort:

1. Keep the filtered signal at 4 kHz or 2 kHz, 16-bit: 0.48 or 0.24 MB per minute, 12 to 24 times smaller, no extra loss inside the band.
2. FLAC on top, using Android's built-in encoder: lossless, expected roughly half again. Not measured.
3. A wavelet or beat-template coder as in the papers: 20 times or more beyond that on clean recordings, lossy, and must be checked not to erase murmurs.

Codec 2 and other speech codecs are unsuitable: they model a voice, not a heart.
