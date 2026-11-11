# Background notes

Checked on 2026-11-11. Repository details come from the GitHub search API (licence, language, last push); the code itself was not reviewed.

## Existing projects

| Repository | Licence | Language | Last push | What it is |
|---|---|---|---|---|
| [davidspringer/Springer-Segmentation-Code](https://github.com/davidspringer/Springer-Segmentation-Code) | none stated | Matlab | 2016-03 | S1/S2 segmentation with a duration-dependent hidden Markov model; the reference method of the PhysioNet 2016 challenge |
| [davidspringer/Schmidt-Segmentation-Code](https://github.com/davidspringer/Schmidt-Segmentation-Code) | none stated | Matlab | 2016-02 | Earlier HMM segmentation |
| [mohamadaz11/springer_heart_sound_segmentation_algorithm](https://github.com/mohamadaz11/springer_heart_sound_segmentation_algorithm) | none stated | Python | 2022-07 | Python port of Springer's method |
| [alvgaona/heart-sounds-segmentation](https://github.com/alvgaona/heart-sounds-segmentation) | MIT | Python | 2026-07 | LSTM segmentation |
| [maidens/PhysioNet-2016](https://github.com/maidens/PhysioNet-2016) | MIT | Jupyter | 2017-02 | Small CNN for normal/abnormal classification |
| [MLD3/DTW_physionet2016](https://github.com/MLD3/DTW_physionet2016) | GPL-3.0 | Python | 2021-02 | Classification by temporal alignment |
| [rahultripathi17/digital-stethoscope-android](https://github.com/rahultripathi17/digital-stethoscope-android) | none stated | Java | 2026-08 | Android companion app for an external stethoscope |
| [alisalevin/BluetoothApp](https://github.com/alisalevin/BluetoothApp) | MIT | Java | 2018-10 | Android app for a Bluetooth stethoscope |

Findings: no Android project was found that records heart sounds through the phone's own microphone with reusable, licensed DSP code. The Android projects found are companions to external stethoscopes. The well-known segmentation code has no licence stated, so it can be read for the method but not copied. Nothing from these repositories is used in this app; the only dependency is the Android platform.

## Datasets for later benchmarking

- PhysioNet/CinC Challenge 2016: about 3,000 heart sound recordings labelled normal or abnormal, resampled to 2 kHz, with S1/S2 annotations for part of the set.
- CirCor DigiScope (PhysioNet 2022 challenge): paediatric recordings at 4 kHz with murmur annotations.

Both were recorded with stethoscopes, not phone microphones, so results on them do not carry over to phone recordings without a separate test. Dataset licences must be read before any file is added to this repo.

## Design choices and what they assume

| Choice | Reason | Needs checking on a phone |
|---|---|---|
| UNPROCESSED audio source first | Android defines it as the signal without gain control or noise suppression | Whether the phone supports it and what its low end looks like |
| 48 kHz capture, 4 kHz processing | Heart sounds and most murmurs lie below 600 Hz; 4 kHz leaves a wide margin and is the lowest rate Android plays back | Nothing |
| 20–200 Hz default band | S1 and S2 carry most energy between about 20 and 150 Hz | Many phone microphones are weak below 100 Hz; the band may need to start higher |
| 20–600 Hz option | Murmurs and clicks reach higher | Noise level in this band |
| 4th-order Butterworth | Flat pass band, no ripple, simple and stable | Nothing |
| Minimum-tracking Wiener noise reduction | Heart sounds pause between beats, so the minimum is the noise | Whether real chest recordings have a quiet enough gap; effect on murmurs |
| No automatic gain | Beat-to-beat loudness is information | Whether raw levels are high enough for 16 bits |

## Compression

No standard or widely used codec exists for heart sounds. Published work is research only:

- Tang et al., 2016, "Phonocardiogram signal compression using sound repetition and vector quantization" ([PubMed 26871603](https://pubmed.ncbi.nlm.nih.gov/26871603/)): exploits the beat-to-beat repetition; reported compression ratios of 20 to 149 for normal recordings, 14 to 35 with moderate murmurs, lower with heavy murmurs.
- Chien et al., 2020, "Phonocardiography Signals Compression with Deep Convolutional Autoencoder" ([Applied Sciences 10(17):5842](https://www.mdpi.com/2076-3417/10/17/5842)).
- Wavelet transform coding of heart sounds, several papers.

Only search-result summaries of these were read, not the full papers. The practical route for this app is in PLAN.md, next step 3.
