"""Point segmentation. M1: windows come purely from clicker presses."""

from __future__ import annotations

POST_ROLL_S = 1.0  # covers Bluetooth latency and a slow thumb
SEG_CONF_FULL_GAP = 0.9  # window reaches back to the previous point, so the whole point is inside
SEG_CONF_PRE_ROLL = 0.5  # window was capped by the pre-roll, so a long rally may lose its serve


def resolve_markers(markers: list[dict]) -> tuple[list[dict], list[str]]:
    """Apply `undo` events in time order. Returns the surviving point_won markers and warnings."""
    presses: list[dict] = []
    warnings: list[str] = []
    for m in sorted(markers, key=lambda m: m["t"]):
        if m["type"] == "point_won":
            presses.append(m)
        elif m["type"] == "undo":
            if presses:
                presses.pop()
            else:
                warnings.append(f"undo at {m['t']:.1f}s has no point to undo; ignored")
        else:
            warnings.append(f"unknown marker type {m['type']!r} at {m['t']:.1f}s; ignored")
    return presses, warnings


def points_from_presses(presses: list[dict], duration: float, pre_roll: float) -> list[dict]:
    """One point per press: from max(previous point's end, press - pre_roll) to press + POST_ROLL_S."""
    points = []
    prev_end = 0.0
    for idx, press in enumerate(presses):
        t = press["t"]
        if t > duration:
            raise ValueError(f"press at {t:.1f}s is after the end of the video ({duration:.1f}s)")
        start = max(prev_end, t - pre_roll)
        end = min(t + POST_ROLL_S, duration)
        points.append({
            "idx": idx,
            "start": round(start, 3),
            "end": round(end, 3),
            "serve_t": None,
            "winner": press["player"],
            "winner_source": "clicker",
            "seg_conf": SEG_CONF_FULL_GAP if start == prev_end else SEG_CONF_PRE_ROLL,
        })
        prev_end = end
    return points
