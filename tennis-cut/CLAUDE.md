# CLAUDE.md — tennis-cut (personal, never released)

## Mission
One Android phone records singles tennis; a laptop Python pipeline cuts points,
keeps score, renders clips + condensed video + report. Read docs/BRIEF.md first,
then docs/DECISIONS.md (where we deviate from the brief, and why).

## How to work
- Plan before acting: for any task, first write a short plan (files to touch,
  approach, test) and wait for "go" unless the prompt says "proceed".
- Minimal diffs. Touch only what the task needs. No drive-by refactors.
- No dead code, no commented-out code, no speculative abstractions, no TODO stubs.
- Be direct. If an instruction is a bad idea, say so and propose the better option.
- Prefer boring, proven tools: CameraX, Media3, desktop ffmpeg, OpenCV, PyTorch.
- Never add FFmpegKit (retired 2025). On-phone media = Media3 Transformer.
- Times are seconds from video PTS, never frame_index/fps.
- Every pipeline stage: pure function in, JSON out, unit test with fixtures.
- Confidence fields are mandatory on inferred values; stats ignore conf < 0.7.
- Clicker markers are ground truth and are never overwritten by inference.
- The session folder contract (setup.json, markers.json, calib.json) is in README.md;
  the phone app and the pipeline must agree on it.
- Record decisions in docs/DECISIONS.md (one line each, dated).
- Licences: note any non-MIT/Apache dependency in README (YOLO = AGPL).

## Commands
- Android (from android/): ./gradlew :app:installDebug
- Pipeline (from pipeline/): uv run tennis process ../sessions/<id>
- M0 audio check (from pipeline/): uv run tennis hits <video.mp4>
- Tests (from pipeline/): uv run pytest -q
