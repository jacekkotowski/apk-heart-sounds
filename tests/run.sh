#!/bin/bash
# Signal-processing checks on a computer. Needs only a JDK.
set -e
cd "$(dirname "$0")/.."
O=out/test; rm -rf $O; mkdir -p $O
S=app/src/pl/heartsounds
javac -nowarn -d $O $S/Biquad.java $S/Cfg.java $S/PcgDenoiser.java $S/HeartChain.java $S/Limiter.java $S/Wav.java $S/Render.java $S/Scope.java $S/Quality.java $S/Envelope.java $S/Analysis.java $S/Screen.java $S/Report.java tests/DspTest.java tests/Eval.java tests/MakeReport.java
java -cp $O pl.heartsounds.DspTest $O
