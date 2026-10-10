# Decisions

One line each, newest last. Where a line contradicts docs/BRIEF.md, this file wins.

- 2026-10-10 Project lives in `tennis-cut/` inside hello-world-demo for now; move it out with `git subtree split --prefix tennis-cut` when it gets its own repo.
- 2026-10-10 Audio onsets use NumPy/SciPy spectral flux (band 1–8 kHz, computed in the FFT) instead of librosa: same method, no numba/llvmlite.
- 2026-10-10 The phone writes `setup.json` (players, format, first server, ends, entered final score) next to `markers.json` and `calib.json`; the pipeline writes only to `<session>/out/` and never edits inputs.
- 2026-10-10 `markers.json` is the full press log, `undo` events included; the pipeline resolves undos, so the phone never rewrites history.
- 2026-10-10 M1 point windows: end = press + 1 s; start = max(previous point's end, press − pre-roll), pre-roll 20 s by default (`--pre-roll`).
- 2026-10-10 Scoreboard is burned into each clip in the one encode; condensed.mp4 is a stream-copy concat of the clips. This drops the brief's 0.2 s crossfade, which would cost a second full encode.
- 2026-10-10 Ingest skips the 512×288 ML proxy until M2; nothing in M1 reads it.
- 2026-10-10 (M1b) Confirm presses with a beep, not haptics: the phone is on the fence, so nobody feels a vibration and it shakes the frame the homography depends on.
- 2026-10-10 (M1b) Undo = long press or 3 quick presses: many cheap shutter remotes send key-down/up instantly however long you hold.
- 2026-10-10 The owner is not technical and chose phone-only: the app makes the highlight video itself (Media3 Transformer, scoreboard as a bitmap overlay, saved to the Gallery). The laptop pipeline stays for M2+ analysis and reads the same session folder.
- 2026-10-10 The app has big on-screen "won"/undo buttons as well as clicker support, for testing before a clicker arrives.
- 2026-10-10 Court-corner calibration is left out of the app until M2 needs it; it adds a setup step the owner gains nothing from yet.
- 2026-10-10 APK signing key is committed (android/app/tenniscut.keystore) so every build installs over the last one. Personal sideload only; never reuse it.
- 2026-10-10 `a_starts_near` in setup.json is optional (default true); the app does not ask for it.
