"""`tennis hits <video>` (M0 spike) and `tennis process <session>` (M1, marker-only)."""

from __future__ import annotations

import argparse
import csv
import json
import shutil
import sys
from pathlib import Path

from . import audio_hits, ingest, render, segment
from . import score as sc


def hits(video: Path, threshold: float) -> None:
    found = audio_hits.detect_hits(ingest.load_audio(video), ingest.AUDIO_SR, threshold)
    csv_path = video.with_suffix(".hits.csv")
    with csv_path.open("w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["t", "conf"])
        writer.writeheader()
        writer.writerows(found)
    srt_path = video.with_suffix(".srt")
    srt_path.write_text(audio_hits.to_srt(found), encoding="utf-8")
    minutes = ingest.probe(video)["duration"] / 60
    print(f"{len(found)} hit candidates ({len(found) / minutes:.1f}/min) -> {csv_path.name}, {srt_path.name}")
    print(f"Open {video.name} in VLC; it loads {srt_path.name} automatically and flashes HIT at each detection.")


def process(session: Path, pre_roll: float) -> None:
    setup = json.loads((session / "setup.json").read_text())
    markers = json.loads((session / "markers.json").read_text())
    calib_path = session / "calib.json"
    video_path = session / setup.get("video", "match.mp4")
    video = ingest.probe(video_path)
    fmt = sc.Format(**setup["format"])
    names = {p["id"]: p["name"] for p in setup["players"]}

    presses, warnings = segment.resolve_markers(markers)
    points = segment.points_from_presses(presses, video["duration"], pre_roll)
    state = sc.initial(setup["first_server"], setup["a_starts_near"])
    boards = []
    after_match = 0
    for p in points:
        boards.append(render.scoreboard_ass((names["A"], names["B"]), state, fmt, video["width"], video["height"]))
        p["server"] = sc.server(state, fmt)
        if state.winner is None:
            state = sc.apply_point(state, fmt, p["winner"])
        else:
            after_match += 1
        p["score_after"] = sc.display(state, fmt)
    if after_match:
        warnings.append(f"{after_match} press(es) after match point; cut as clips, not scored")
    entered = setup.get("final_score_entered")
    clicked = [list(s) for s in state.sets] + ([list(state.games)] if state.games != (0, 0) else [])
    if entered and entered != clicked:
        warnings.append(f"clicker score {clicked} differs from entered final score {entered}: "
                        "look for a missed or doubled press")

    out = session / "out"
    clips_dir = out / "clips"
    shutil.rmtree(clips_dir, ignore_errors=True)
    clips_dir.mkdir(parents=True)
    clips = []
    for p, board in zip(points, boards):
        clip = clips_dir / f"{p['idx'] + 1:03d}.mp4"
        print(f"\rRendering point {p['idx'] + 1}/{len(points)}", end="", flush=True)
        render.render_clip(video_path, p["start"], p["end"], board, clip)
        clips.append(clip)
    print()
    if clips:
        render.concat(clips, out / "condensed.mp4")

    result = {
        "schema": 1,
        "session_id": setup.get("session_id", session.name),
        "video": {"file": video_path.name, **video},
        "players": setup["players"],
        "format": setup["format"],
        "first_server": setup["first_server"],
        "a_starts_near": setup["a_starts_near"],
        **({"calib": json.loads(calib_path.read_text())} if calib_path.exists() else {}),
        "markers": markers,
        "points": points,
        "final_score_entered": entered,
    }
    (out / "session.json").write_text(json.dumps(result, indent=2), encoding="utf-8")

    played = sum(p["end"] - p["start"] for p in points)
    print(f"{len(points)} points, {played / 60:.1f} of {video['duration'] / 60:.1f} min kept -> {out}")
    for w in warnings:
        print(f"warning: {w}", file=sys.stderr)


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="tennis")
    sub = parser.add_subparsers(dest="command", required=True)
    p_hits = sub.add_parser("hits", help="M0: find racket-hit candidates in a video's audio")
    p_hits.add_argument("video", type=Path)
    p_hits.add_argument("--threshold", type=float, default=audio_hits.DEFAULT_THRESHOLD,
                        help="robust z-score a peak must reach; lower finds more (default %(default)s)")
    p_proc = sub.add_parser("process", help="cut points from clicker markers; render clips and condensed video")
    p_proc.add_argument("session", type=Path)
    p_proc.add_argument("--pre-roll", type=float, default=20.0,
                        help="seconds kept before each press, capped at the previous point (default %(default)s)")
    args = parser.parse_args(argv)
    if args.command == "hits":
        hits(args.video, args.threshold)
    else:
        process(args.session, args.pre_roll)
