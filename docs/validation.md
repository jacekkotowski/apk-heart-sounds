# Validation

Last updated: 2026-11-12

## What was tested

The beat finder and the murmur-like-sound flag (`Analysis.java`, `Screen.java`) were run on the CirCor DigiScope dataset, version 1.0.3, from PhysioNet (Open Data Commons Attribution License v1.0). No dataset file is in this repo.

- Recordings: 1,749, from all 179 patients labelled "murmur present" and 330 randomly chosen patients labelled "murmur absent". Patients labelled "unknown" were left out.
- These are stethoscope recordings at 4 kHz, mostly of children. They are not phone recordings and not adults.
- Patients were split at random into two halves. The flag limit was chosen on one half and the results below are from the other half, which the limit never saw.
- A recording counts as "murmur" when its patient has a systolic murmur and the recording was made at a spot where the murmur was audible according to the dataset. Recordings from murmur patients at other spots were left out. Only 5 patients have a diastolic murmur, too few to test, so the app has no flag for sound after the second heart sound.

Reproduce: download the recordings, then `java -cp out/test pl.heartsounds.Eval <folder>` after `bash tests/run.sh` and compiling `tests/Eval.java`.

## Beat finding (all 1,749 recordings)

| Measure | Result |
|---|---|
| Recordings where beats were found reliably | 90% |
| Heart rate against the dataset's annotation, reliable recordings: median error | 1.4 per minute |
| Within 5 per minute | 89% |
| Within 10% | 97% |
| Recordings with at least 80% of first-sound marks on an annotated first sound | 78% |
| Recordings with first and second sound swapped | 9% |

A swap does not change the heart rate, but it makes the flag look in the wrong gap, so a murmur is missed.

## The flag

Rule: flag when the sound between the first and second heart sound (150–600 Hz) is at least 3 dB above the gap after the second heart sound, as the middle value over all beats. The 3 dB limit was the setting on the tuning half with the most murmurs found while wrongly flagging at most 10% of recordings without one.

Held-out half, 829 recordings from 261 patients, every recording counted:

| Dataset label | Recordings | Flagged | Not flagged | Cannot tell |
|---|---|---|---|---|
| Murmur at this spot | 237 | 139 (59%) | 75 (32%) | 23 (10%) |
| No murmur | 592 | 22 (4%) | 492 (83%) | 78 (13%) |

Among recordings where the beats were found: 65% of murmurs flagged (139 of 214), 96% of non-murmurs not flagged (492 of 514). Of the flagged recordings, 86% had a murmur; in this test set 29% of recordings had one, far more than among people in general, so in ordinary use a much smaller share of flags will be real.

By loudness grade, murmurs flagged: grade I 42% (of 80), grade II 66% (of 74), grade III 93% (of 60).

## Re-run for 0.3.0 (2026-11-12)

`Analysis.java` was changed for the report: it now also returns its loudness curves, per-beat values and the typical beat in the higher band, and one array was enlarged. The enlargement fixes a crash found in this re-run: recording 50655_AV stopped the old code with an array overflow when the beat length drifted to its lower limit.

Old and new code were run on all 3,163 recordings of the dataset and the output rows compared.

| Result | Recordings |
|---|---|
| Identical row (rate, timing, flag measure, flag) | 3,156 |
| Old code crashed, new code answers "cannot tell" | 1 |
| Different numbers, "cannot tell" before and after | 6 |
| Flag or reliability changed | 0 |

The six differ because the larger array lets beat tracking run to the end of the recording at very short beat lengths. The tables above therefore stand. The random patient subset and split of the first run were not stored, so those tables were not recomputed row by row.

## What this does not show

- Nothing about phones. A phone microphone has less bass, more noise and no chest piece. The one phone recording available (30 s, one adult) gave 81 beats per minute, 34 beats, and no flag; there is no reference to compare it with.
- Nothing about adults.
- Nothing about any disease. The dataset labels are "murmur heard", and many murmurs in children are harmless. The app does not name a disease and has no basis to.
- Nothing about murmurs after the second heart sound, extra heart sounds or rhythm disorders.
- The "unusual beat" marks were not validated at all. They compare each beat with the recording's own typical beat and mostly catch knocks and movement.

## Before trusting the flag on a phone

Record the same people with the phone and with an electronic stethoscope, with a clinician's finding as the reference, and repeat this table for the phone recordings.
