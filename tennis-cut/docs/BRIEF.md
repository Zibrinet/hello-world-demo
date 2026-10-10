# tennis-cut — Project Brief

### 1. Executive summary
**Product goal (one sentence):** Record a singles match on one Android phone. Within 30 minutes of walking off court, have every point as its own clip, a condensed match video with a correct running score burned in, and a one-screen stats summary.

The client is a high-net-worth private player who wants SwingVision-style results on Android, fast. We deliver in four milestones. Each one is usable on its own. Capture is on-device; analysis runs in a laptop Python pipeline; the human taps a clicker once per point so the score is always right. We are not doing line calling.

### 2. Scope

**MVP must-haves (M1–M3)**
1. Android capture app: 1080p60 recording, a 4-corner court calibration tap, point-winner clicker logging, a stealth black screen, and battery/thermal readouts.
2. Laptop pipeline: automatic point segmentation (start = serve, end = last hit + bounce + pad), with manual markers as overrides.
3. Outputs:
   - per-point MP4s;
   - a condensed match MP4 with a scoreboard overlay;
   - `session.json`;
   - a static HTML report viewable on phone or laptop.
4. Scoring state machine for tennis (ad and no-ad, tiebreaks, match tiebreak).
5. Stats:
   - points won;
   - games/sets;
   - rally length (hits) distribution;
   - average/longest rally;
   - point duration;
   - serve points won;
   - 1st-serve-in % and double faults (from serve-event counts);
   - longest-rally highlights.

**Phase 2 (M4+)**
- Ball tracking overlay.
- Bounce/depth heatmaps.
- Forehand/backhand/serve/volley classification.
- Rough serve speed.
- Auto-highlights.
- Doubles.
- On-device processing.

**Non-goals**
- Line calls.
- Spin.
- Real-time on-court feedback.
- Multi-camera.
- Cloud accounts.
- Play Store release.
- iOS.
- Pickleball.

### 3. User stories and on-court workflow
- *As the client, I* mount the phone, open the app, tap 4 court corners on the live preview, pick the server and ends, and hit Record. Target: under 2 minutes.
- *During play*, after each point the winner presses the clicker: 1 press = "me", 2 presses = "opponent", long press = "undo last". The app vibrates or beeps once to confirm. Nothing else is required.
- *At changeover*, nothing. The app tracks ends from the score.
- *At the end*, I tap Stop and enter the final set scores (used as a check). The app writes `session/` (video + `markers.json` + `calib.json`).
- *At home*, I run `tennis process <session>` (or drop the folder into a watched directory). Within 30 minutes I get clips, a condensed video and a report. I open `report.html` on my phone.
- *Review*, I can fix a wrong point in the report (edit winner, or merge/split points), and the outputs re-render.

### 4. Architecture and tech stack

**Phone (Kotlin)**
- Kotlin, Jetpack Compose, CameraX 1.5 (`VideoCapture` + `Recorder`, Quality.FHD, 60 fps via `SessionConfig` frame-rate range when the device supports it, else 30), AAC audio on.
- Single-activity app. Room is unnecessary; use JSON files.
- Media3 ExoPlayer for review. Media3 Transformer only if on-phone clip export is needed later.
- **Transfer:** USB/`adb pull` or a shared folder (Syncthing). No custom networking in v1.

**Laptop (Python 3.11, uv)**
- PyTorch (CUDA, or MPS on a Mac), OpenCV, NumPy, SciPy, librosa, desktop ffmpeg.
- **Ball:** WASB-SBDT `wasb_tennis_best.pth.tar` (MIT); optionally TrackNetV3 InpaintNet (MIT) for gaps.
- **Court:** manual corners → `cv2.findHomography`; optional TennisCourtDetector refine.
- **Players:** YOLO11n person detection (AGPL-3.0, OK for private use; flag it in the README).
- **Audio hits:** librosa onset detection; then a logistic-regression or YAMNet-embedding classifier trained on the client's own hits.

**Data flow**
```
[Phone] CameraX 1080p60 + AAC ─► match.mp4
        corner taps ───────────► calib.json (4 image pts → court model)
        clicker presses ───────► markers.json (t, type, player)
            │  adb pull / Syncthing
            ▼
[Laptop] ingest ─► audio.wav (16k mono) + proxy.mp4 (512x288@60)
         ├─► audio_hits.py ─► hits[]  (t, conf)
         ├─► ball_track.py ─► ball[]  (frame, x, y, vis) ─► bounces[]
         ├─► players.py   ─► players[] (frame, near/far, court_xy)
         ├─► segment.py   ─► points[] (start, end, serve_t, conf)  ◄─ markers override
         ├─► score.py     ─► score state per point (+ final-score reconcile)
         ├─► stats.py     ─► stats.json
         └─► render.py    ─► clips/*.mp4, condensed.mp4 (score overlay), report.html
```

**Repo layout**
```
tennis-cut/
  CLAUDE.md
  docs/BRIEF.md  docs/DECISIONS.md
  android/                 # Gradle project, app module only
    app/src/main/java/.../{capture,calib,markers,ui}/
  pipeline/
    pyproject.toml
    tennis/{ingest,audio_hits,ball_track,players,court,segment,score,stats,render,report}.py
    tennis/cli.py
    tests/  fixtures/      # 3-minute labelled sample clips
  models/                  # gitignored weights + download script
  sessions/                # gitignored
  eval/{labels/,run_eval.py}
```

**Data model (`session.json`, schema v1)**
```json
{
  "schema": 1,
  "session_id": "2026-10-12_1630_court3",
  "video": {"file": "match.mp4", "fps": 60, "width": 1920, "height": 1080, "start_epoch_ms": 0},
  "players": [{"id": "A", "name": "Michael"}, {"id": "B", "name": "Friend"}],
  "format": {"sets": 3, "no_ad": false, "final_set": "match_tiebreak"},
  "first_server": "A", "a_starts_near": true,
  "calib": {"image_pts": [[x,y],[x,y],[x,y],[x,y]], "court_pts_m": "singles_corners", "H": [[...]], "reproj_err_px": 1.8},
  "markers": [{"t": 123.45, "type": "point_won", "player": "A", "source": "clicker"}],
  "points": [{
    "idx": 0, "start": 118.2, "end": 126.9, "serve_t": 119.6,
    "server": "A", "serve_number": 1, "faults": 0,
    "winner": "A", "winner_source": "clicker|inferred|reconciled|edited",
    "hits": [{"t": 119.6, "player": "A", "type": "serve", "conf": 0.93}],
    "bounces": [{"t": 120.3, "court_xy_m": [1.2, 17.8], "conf": 0.7}],
    "rally_len": 5, "seg_conf": 0.91,
    "score_after": {"sets": [[0,0]], "games": [0,0], "points": "15-0"}
  }],
  "final_score_entered": [[6,4],[3,6],[10,7]],
  "stats": {}
}
```

### 5. Processing pipeline, stage by stage
1. **Ingest.**
   - Run `ffprobe` to read fps/VFR. Phones often record variable frame rate, so always map times through PTS, never frame index × fps.
   - Extract 16 kHz mono WAV.
   - Make a 512×288 proxy for ML.
2. **Court.**
   - Map the 4 tapped corners (singles-sideline/baseline intersections) to metric court coordinates (23.77 m × 8.23 m) and compute the homography.
   - Reject if reprojection of the other line intersections is worse than 8 px; then prompt a re-tap in the report.
   - Optional: TennisCourtDetector refine, keeping it only if its error is lower.
3. **Audio hits.**
   - Band-pass about 1–8 kHz, compute the onset-strength envelope, then adaptive-threshold peak picking (min gap 0.35 s).
   - v2: a classifier on 0.25 s windows trained on hits from the client's labelled sessions, which rejects shoe squeaks and neighbour courts.
   - Output `(t, conf)`.
4. **Ball.**
   - Run WASB only in windows of ±3 s around audio-hit clusters, which saves about 60–70% of compute.
   - Kalman-link detections into tracks.
   - Bounce = sign change of image-y velocity with low speed at the turning point, or a CatBoost classifier later.
   - Project bounces with H.
5. **Players.**
   - YOLO person boxes at 10 fps; bottom-centre of each box → H → court xy.
   - Assign near/far by court y; hold IDs by side, swapping on changeovers from the score state.
6. **Segmentation.**
   - Cluster hits with gap ≤ 4.5 s into rally candidates.
   - Start = first hit (the serve) − 2.5 s, which includes the toss.
   - End = max(last hit, last bounce) + 2.0 s.
   - Discard candidates with fewer than 1 hit, or with no ball track and no player motion (warm-up and feeds are handled by a "practice" flag).
   - **Serve detection:** first hit after ≥ 8 s of quiet, with a player behind the baseline. Two serves within 12 s with no rally means a fault followed by a second serve. Merge them into one point and set `faults = 1`.
   - **Markers override:** every clicker press must map to exactly one point, ending 0–15 s before the press. If no candidate exists, create one from the press (end = press − 3 s, start = end − 10 s, flag low confidence). If two candidates map to one press, merge them.
   - Per-point `seg_conf` comes from hit count, track coverage and marker agreement.
7. **Score.**
   - A pure-function tennis state machine fed by point winners in order.
   - If markers are missing, fall back to the inferred winner. Inference: hit-parity gives the last hitter; the last bounce out or into the net favours the other player; confidence is ≤ 0.6.
   - Reconcile against the entered final score with a DP/beam search over minimal flips of the lowest-confidence inferred points. This is the SwingVision "Final Score" idea. Clicker points are never flipped.
8. **Stats.** Computed only from fields with confidence ≥ 0.7. Show "n/a" rather than guess.
9. **Render.**
   - Per-point clips: ffmpeg re-encode (`-ss` before `-i`, libx264 `-crf 20`, preset veryfast) for frame accuracy.
   - Condensed video: concat filter with an ASS-subtitle scoreboard (names, sets, games, points, server dot) and a 0.2 s crossfade.
   - Report: static HTML with a point list (thumbnail, duration, rally length, winner, edit buttons writing `edits.json`) and stats tables.

### 6. Milestones (fastest useful result first)

| M | Deliverable | Acceptance criteria | Effort |
|---|---|---|---|
| M0 | Stock-camera spike | Record 20 min with the stock camera at 1080p60 on the fence; log temperature and battery %; run audio onset detection in a notebook and eyeball hits | 1 evening |
| M1 | Capture app + markers + cutter | App records 90 min without stopping, thermal warning ≤ "moderate"; clicker logs winners with < 0.3 s latency; `tennis cut` produces per-point clips purely from markers; condensed video with score overlay | 2–3 days |
| M2 | Auto segmentation | On 3 labelled sessions: point recall ≥ 95%, precision ≥ 95%, median boundary error ≤ 1.0 s, serve never clipped in ≥ 98% of points; runtime ≤ 30 min per 90-min match on the laptop | 2–4 days |
| M3 | Score + stats + report | Score matches the entered final score in 100% of clicker sessions; serve faults detected ≥ 90%; report renders on phone; edits re-render in < 2 min | 2–3 days |
| M4 | Vision Phase 2 | Ball overlay, bounce heatmap, FH/BH/serve labels at ≥ 85% on labelled hits; serve speed shown with an "est." label | 1–2 weeks |

### 7. Test and evaluation plan
- **Data:** 3 sessions at first (one outdoor midday, one outdoor late afternoon, one indoor), 45–90 minutes each.
- **Ground truth:**
  - The M1 clicker markers give point ends and winners for free.
  - Hand-label point start (serve toss frame) and end for each session in a CSV via the report UI (about 20 minutes per session).
  - Label 300 hits and 300 non-hit audio windows for the hit classifier.
  - For ball fine-tuning, label 1–2k frames in CVAT or Label Studio, [github](https://github.com/changtimwu/ballvideo3dreconstruction/issues/1) biased toward the far court.
- **Metrics (`eval/run_eval.py`):**
  - point precision/recall at IoU ≥ 0.5;
  - start/end absolute error (median, p90);
  - "clipped serve" rate;
  - false cuts per hour (splits inside a rally);
  - phantom points per hour;
  - score accuracy;
  - pipeline wall time.
- **Regression:** a 3-minute fixture clip with golden JSON in `pipeline/tests`; CI-free, run locally before every commit.

### 8. Phone mounting and recording guidance (for the client)
- **Position:** centred behind one baseline, on or above the fence, as high as possible (top of fence, roughly 2.5–3.5 m). Tilt down until both baselines and both singles sidelines are fully in frame with a little margin above the far baseline. Landscape, horizon level, lens 1×, not ultra-wide.
- **Mount:** a rigid fence clamp or a tall tripod lashed to the fence. Wobble ruins the homography. Re-tap the corners if the phone is bumped.
- **Settings:** 1080p60, audio on, HDR video off, stabilisation off (it warps geometry), focus/exposure locked on the court.
- **Power and heat:**
  - Start at 100% plus a power bank.
  - Keep the phone shaded (a white cloth or shade hood) and the case off in heat.
  - The app shows a black screen with the display at minimum.
  - If the thermal status reaches "severe", the app warns and drops to 30 fps rather than stopping.
- **Storage:** keep ≥ 20 GB free per match.
- **Lighting:** avoid shooting into a low sun. Indoors, low light raises motion blur, so expect lower ball recall.

### 9. Risks, realistic accuracy vs SwingVision, mitigations

| Area | SwingVision (vendor) | This build, realistic | Mitigation |
|---|---|---|---|
| Dead-time removal | Strong (users' favourite feature) | 90–97% points auto-found; 100% with clicker | Clicker markers as ground truth/override |
| Score | Watch = accurate; AI scoring often wrong per reviews | ~100% with clicker | Never rely on inferred winners alone |
| Line calls | 97% within 10 cm | **Not offered** | Heatmaps only |
| Shot speed | Measured via 3D physics fit | ±10–20%, labelled "est." | Ground-plane plus serve-time-of-flight only |
| Stroke type | Yes | ~85% FH/BH/serve after tuning | Ball-side-of-body heuristic + pose |
| Thermal | Its own Android blocker | Recording only, so lower load | 30 fps fallback, shade, power bank |

Other risks:
- Neighbour-court audio, handled by audio/visual fusion.
- VFR timestamps, handled by always using PTS.
- AGPL contamination if ever shared; swap YOLO for an Apache-2.0 detector before any distribution.
- The far-court ball is often invisible, so fine-tune on the client's own footage.

### 10. CLAUDE.md
```markdown
# CLAUDE.md — tennis-cut (personal, never released)

## Mission
One Android phone records singles tennis; a laptop Python pipeline cuts points,
keeps score, renders clips + condensed video + report. Read docs/BRIEF.md first.

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
- Record decisions in docs/DECISIONS.md (one line each, dated).
- Licences: note any non-MIT/Apache dependency in README (YOLO = AGPL).

## Commands
- Android: ./gradlew :app:installDebug
- Pipeline: uv run tennis process sessions/<id>
- Tests: uv run pytest -q ; uv run python eval/run_eval.py
```

### Sequenced Claude Code prompts
1. **M0:** "Read docs/BRIEF.md. Create the repo skeleton exactly as in §4 with empty packages, pyproject (uv), and a Gradle Compose app that builds. Plan first."
2. **M1a:** "Implement the CameraX capture screen: 1080p, 60 fps if supported via SessionConfig frame-rate query, else 30; AAC audio; save to app-specific external storage under sessions/<id>/match.mp4; black overlay mode with FLAG_KEEP_SCREEN_ON; show PowerManager thermal status and battery %. Plan first."
3. **M1b:** "Add calibration: freeze a preview frame, tap 4 singles corners in fixed order with a magnifier, save calib.json. Add markers: volume-key events (Bluetooth shutter remote) → 1 press=A, 2 presses within 0.6 s=B, long press=undo; haptic confirm; write markers.json with PTS-relative times."
4. **M1c:** "Pipeline: ingest.py (ffprobe, wav, proxy), segment.py marker-only mode, score.py state machine with full unit tests (ad/no-ad, tiebreak, match tiebreak), render.py per-point clips + condensed video with an ASS scoreboard. CLI `tennis process`."
5. **M2a:** "audio_hits.py: band-pass + onset envelope + peak picking; eval against eval/labels; report precision/recall. Then add the small classifier trained from labelled windows."
6. **M2b:** "Integrate WASB-SBDT tennis weights (download script, MIT notice); run only in windows around hit clusters; Kalman linking; output ball.json. Then segment.py fusion + marker override rules from §5.6; run_eval.py metrics from §7."
7. **M3:** "Serve/fault detection, final-score reconciliation, stats.py, report.html with point editing → edits.json → re-render."
8. **M4:** "Bounces + homography heatmap; YOLO players with near/far; FH/BH/serve heuristic; estimated serve speed. Each behind a flag, each with eval numbers before merging."

## Recommendations
1. **This week:** if you own an Apple-silicon Mac, run one Android-recorded session through SwingVision's Mac import. That is your accuracy benchmark, and it may be enough.
2. **Regardless, buy a ~$10 Bluetooth shutter remote and a rigid fence mount.** The clicker turns the hardest problem (who won the point) into a solved one and gives you labelled data.
3. **Hand Claude Code the brief and stop at M1.** Use it for two matches. Only start M2 once marker-only cutting is in daily use. That ordering gives you a working product after about 3 days.
4. **Keep the laptop architecture.** Revisit on-device processing only after the pipeline hits the M2 targets.

## Caveats
- **Vendor figures.** All SwingVision accuracy and user numbers are vendor claims. No independent lab test was found. The same goes for every alternative's accuracy figures.
- **Patent attribution.** The patents cited match SwingVision's described method, but I did not verify the assignee.
- **Prices.** They vary by country and over time. Ten-Fifty5 and Tennis Cut prices conflict across sources.
- **My estimates.** Effort, runtime and accuracy targets for this build are engineering estimates, not measurements. M0 exists to validate the thermal and audio assumptions on your specific phone.