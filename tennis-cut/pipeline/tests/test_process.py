"""End to end on a synthetic recording: markers -> clips, condensed video, session.json."""

import json
import shutil
import subprocess

import pytest

from tennis import cli, ingest, render
from tennis import score as sc

pytestmark = pytest.mark.skipif(shutil.which("ffmpeg") is None, reason="needs ffmpeg")


def make_video(path, seconds):
    subprocess.run(
        ["ffmpeg", "-nostdin", "-v", "error", "-y",
         "-f", "lavfi", "-i", f"testsrc2=size=320x180:rate=30:duration={seconds}",
         "-f", "lavfi", "-i", f"sine=frequency=440:sample_rate=48000:duration={seconds}",
         "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", str(path)],
        check=True,
    )


@pytest.fixture
def session(tmp_path):
    make_video(tmp_path / "match.mp4", 30)
    (tmp_path / "setup.json").write_text(json.dumps({
        "schema": 1,
        "session_id": "test",
        "players": [{"id": "A", "name": "Me"}, {"id": "B", "name": "Friend"}],
        "format": {"sets": 3, "no_ad": False, "final_set": "match_tiebreak"},
        "first_server": "A",
        "a_starts_near": True,
        "final_score_entered": [[1, 0]],
    }))
    (tmp_path / "markers.json").write_text(json.dumps([
        {"t": 4.0, "type": "point_won", "player": "A", "source": "clicker"},
        {"t": 9.0, "type": "point_won", "player": "B", "source": "clicker"},
        {"t": 10.0, "type": "undo", "source": "clicker"},
        {"t": 11.0, "type": "point_won", "player": "A", "source": "clicker"},
        {"t": 18.0, "type": "point_won", "player": "A", "source": "clicker"},
        {"t": 26.0, "type": "point_won", "player": "A", "source": "clicker"},
    ]))
    return tmp_path


def test_process_cuts_scores_and_concatenates(session, capsys):
    cli.main(["process", str(session), "--pre-roll", "5"])
    out = session / "out"
    result = json.loads((out / "session.json").read_text())
    points = result["points"]

    assert [(p["start"], p["end"]) for p in points] == [(0, 5), (6, 12), (13, 19), (21, 27)]
    assert [p["score_after"]["points"] for p in points] == ["15-0", "30-0", "40-0", "0-0"]
    assert points[-1]["score_after"]["games"] == [1, 0]
    assert [p["server"] for p in points] == ["A"] * 4

    clips = sorted((out / "clips").glob("*.mp4"))
    assert [c.name for c in clips] == ["001.mp4", "002.mp4", "003.mp4", "004.mp4"]
    for clip, p in zip(clips, points):
        assert ingest.probe(clip)["duration"] == pytest.approx(p["end"] - p["start"], abs=0.1)
    assert ingest.probe(out / "condensed.mp4")["duration"] == pytest.approx(23, abs=0.3)
    assert "warning" not in capsys.readouterr().err


def test_mismatched_final_score_warns(session, capsys):
    setup = json.loads((session / "setup.json").read_text())
    setup["final_score_entered"] = [[6, 4]]
    (session / "setup.json").write_text(json.dumps(setup))
    cli.main(["process", str(session), "--pre-roll", "5"])
    assert "differs from entered final score" in capsys.readouterr().err


def test_hits_writes_csv_and_srt(tmp_path):
    video = tmp_path / "practice.mp4"
    make_video(video, 5)
    cli.main(["hits", str(video)])
    assert (tmp_path / "practice.hits.csv").read_text().startswith("t,conf")
    assert (tmp_path / "practice.srt").exists()


def test_scoreboard_shows_names_sets_and_server():
    fmt = sc.Format(sets=3)
    state = sc.Score(sets=((6, 4),), games=(2, 3), points=(3, 1), game_server="B")
    ass = render.scoreboard_ass(("Me", "Fr{iend}"), state, fmt, 1920, 1080)
    texts = [line.rsplit(",,", 1)[1] for line in ass.splitlines() if line.startswith("Dialogue")]
    plain = [t.split("}", 1)[1] for t in texts if "\\p1" not in t]
    assert plain == ["Me", "6", "2", "40", "•", "Friend", "4", "3", "15"]
