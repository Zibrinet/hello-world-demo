import pytest

from tennis import segment


def press(t, player="A"):
    return {"t": t, "type": "point_won", "player": player, "source": "clicker"}


def undo(t):
    return {"t": t, "type": "undo", "source": "clicker"}


def test_undo_removes_previous_press_in_time_order():
    markers = [press(30, "A"), press(10, "B"), undo(31), press(32, "B")]
    presses, warnings = segment.resolve_markers(markers)
    assert [(p["t"], p["player"]) for p in presses] == [(10, "B"), (32, "B")]
    assert warnings == []


def test_undo_with_nothing_to_undo_warns():
    presses, warnings = segment.resolve_markers([undo(5), press(10)])
    assert len(presses) == 1
    assert "no point to undo" in warnings[0]


def test_windows_use_pre_roll_and_never_overlap():
    points = segment.points_from_presses([press(30), press(45, "B"), press(100)], duration=200, pre_roll=20)
    assert [(p["start"], p["end"]) for p in points] == [(10, 31), (31, 46), (80, 101)]
    assert [p["seg_conf"] for p in points] == [0.5, 0.9, 0.5]
    assert [p["winner"] for p in points] == ["A", "B", "A"]
    assert all(p["winner_source"] == "clicker" for p in points)


def test_first_point_starts_at_video_start_and_last_is_clamped():
    points = segment.points_from_presses([press(5), press(99.5)], duration=100, pre_roll=20)
    assert points[0]["start"] == 0 and points[0]["seg_conf"] == 0.9
    assert points[1]["end"] == 100


def test_press_after_video_end_is_an_error():
    with pytest.raises(ValueError):
        segment.points_from_presses([press(120)], duration=100, pre_roll=20)
