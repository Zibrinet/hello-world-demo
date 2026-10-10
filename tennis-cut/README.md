# tennis-cut

You record a singles match on one Android phone and tap a Bluetooth clicker after each point. A laptop pipeline then cuts every point into its own clip, keeps the score and renders a condensed match video with the scoreboard burned in. Plan: [docs/BRIEF.md](docs/BRIEF.md). Deviations from it: [docs/DECISIONS.md](docs/DECISIONS.md).

| Milestone | State |
|---|---|
| M0 spike | `tennis hits` is ready. You still need to do the recording, heat test and clicker range test (below). |
| M1 pipeline (M1c) | Done: marker-only cutting, the tennis scoring state machine, per-point clips and the condensed video. |
| M1 phone app (M1a/M1b) | Not started. It needs the Android SDK and your phone. Its spec is the session folder format below. |

## Setup

You need [uv](https://docs.astral.sh/uv/) and an ffmpeg build with libass. The Homebrew and Ubuntu builds both have it.

```sh
cd pipeline
uv sync
uv run pytest -q
```

## M0: is the phone and audio plan viable?

Do this before building the phone app.

1. **Heat and battery.** Mount the phone as described in BRIEF §8 and record at least 20 minutes of real play at 1080p60 with the stock camera. Note the start and end battery %, the air temperature, and whether the camera warns about heat, drops frames or stops.
2. **Clicker range.** Do this separately, because a press stops the stock camera's video. Pair the shutter remote and put the camera in photo mode. Press from the far baseline and from both far corners, 10 times each. The far baseline is about 25–30 m from the phone, and cheap remotes are often rated for about 10 m. If presses go missing out there, fix that first (try a different remote, or find where it is reliable), because the whole score depends on it.
3. **Audio hits.**
   ```sh
   uv run tennis hits /path/to/match.mp4
   ```
   This writes `match.hits.csv` and `match.srt` next to the video. Open the video in VLC: it loads the `.srt` and flashes `HIT` at each detection. Over about 5 minutes of rallies, count the missed hits and the false ones (bounces, squeaks, the next court). `--threshold 4` finds more and `--threshold 7` finds fewer; the default is 5.

Write down the results in docs/DECISIONS.md. They decide whether M2 can rely on audio.

## Session folder

The phone app writes one folder per match. The pipeline reads it and writes only to `out/`.

```
sessions/2026-10-12_1630_court3/
  match.mp4       # 1080p60 (30 if 60 is unsupported), AAC audio
  setup.json      # chosen before recording; final score added at Stop
  markers.json    # every clicker press, in order
  calib.json      # 4 tapped court corners (used from M2 on)
  out/            # written by `tennis process`
```

`setup.json`:
```json
{
  "schema": 1,
  "session_id": "2026-10-12_1630_court3",
  "video": "match.mp4",
  "players": [{"id": "A", "name": "Me"}, {"id": "B", "name": "Friend"}],
  "format": {"sets": 3, "no_ad": false, "final_set": "match_tiebreak"},
  "first_server": "A",
  "a_starts_near": true,
  "final_score_entered": [[6, 4], [3, 6], [10, 7]]
}
```

- `sets` is the best-of count: 1, 3 or 5.
- `final_set` is `tiebreak`, `match_tiebreak` (first to 10) or `advantage`.
- `final_score_entered` is optional. It is a check: if it disagrees with the clicker score you get a warning. If you stop mid-set, include that set's current games, e.g. `[[6, 4], [4, 3]]`.

`markers.json`:
```json
[
  {"t": 123.45, "type": "point_won", "player": "A", "source": "clicker"},
  {"t": 151.02, "type": "point_won", "player": "B", "source": "clicker"},
  {"t": 152.80, "type": "undo", "source": "clicker"}
]
```
- `t` is seconds since the start of the recording, matching the video's timestamps.
- `undo` removes the latest surviving `point_won`. The phone only ever appends events.

`calib.json`:
```json
{"image_pts": [[412, 1003], [1508, 1001], [1131, 371], [789, 372]], "court_pts_m": "singles_corners"}
```
`image_pts` are pixels in the recorded video, in this order as seen from the camera: near-left, near-right, far-right, far-left.

## Cutting a match

```sh
uv run tennis process ../sessions/2026-10-12_1630_court3
```

It writes these files to `out/`:
- `clips/001.mp4`, `clips/002.mp4`, … : one per point, with the score before that point burned in.
- `condensed.mp4`: all the clips back to back.
- `session.json`: points, winners, servers and the running score (schema v1 from BRIEF §4).

How each clip is cut:
- It ends 1 s after the press.
- It starts 20 s before the press, or where the previous clip ended if that is later.
- If serves get cut off on long rallies, raise `--pre-roll`. If clips carry too much dead time, lower it.

M2's audio segmentation replaces this guess with the real serve time.

Before the phone app exists, you can try this on a stock-camera recording: write `setup.json` and `markers.json` by hand while watching it.

## Licences

- NumPy and SciPy are BSD-3; pytest is MIT.
- ffmpeg is a system tool, not a Python dependency. Common builds are GPL, which is fine for private use.
- YOLO (planned for M4) is AGPL-3.0. Swap it for an Apache-2.0 detector before sharing this with anyone.
