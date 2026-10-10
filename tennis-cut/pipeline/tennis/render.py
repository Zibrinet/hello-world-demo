"""Per-point clips with a burned-in scoreboard, and the condensed match video.

Each clip is encoded once with its scoreboard; condensed.mp4 is a stream-copy
concat of the clips, so a match costs one encode pass in total.
"""

from __future__ import annotations

import subprocess
import tempfile
from pathlib import Path

from . import score as sc

ENCODE_ARGS = [
    "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p",
    "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart",
]
NAME_MAX_CHARS = 14
WHITE = "&H00FFFFFF"
YELLOW = "&H0000E5FF"
BOX = "&H4D000000"  # black, 30% transparent


def scoreboard_ass(names: tuple[str, str], state: sc.Score, fmt: sc.Format, width: int, height: int) -> str:
    """ASS subtitle that draws the score as it stands before a point, top-left, for the whole clip."""
    u = height / 1080
    x0 = y0 = 32 * u
    pad, row_h, dot_w, name_w, col_w, pts_w = (v * u for v in (14, 52, 28, 250, 50, 76))

    columns = [[str(s[0]), str(s[1])] for s in state.sets]
    if not sc.is_match_tiebreak(state, fmt):
        columns.append([str(state.games[0]), str(state.games[1])])
    points = sc.point_labels(state, fmt)
    serving = sc.server(state, fmt)

    box_w = 2 * pad + dot_w + name_w + col_w * len(columns) + pts_w
    box_h = 2 * pad + 2 * row_h
    rect = f"m 0 0 l {box_w:.0f} 0 {box_w:.0f} {box_h:.0f} 0 {box_h:.0f}"
    events = [(0, "Box", f"{{\\an7\\pos({x0:.0f},{y0:.0f})\\p1}}{rect}")]
    for row, player in enumerate(sc.PLAYERS):
        y = y0 + pad + row_h * (row + 0.5)
        x = x0 + pad
        if player == serving:
            events.append((1, "Text", f"{{\\an5\\pos({x + dot_w / 2:.0f},{y:.0f})\\c{YELLOW}&}}•"))
        x += dot_w
        events.append((1, "Text", f"{{\\an4\\pos({x:.0f},{y:.0f})}}{_ass_text(names[row])}"))
        x += name_w
        for col in columns:
            x += col_w
            events.append((1, "Text", f"{{\\an6\\pos({x:.0f},{y:.0f})}}{col[row]}"))
        x += pts_w
        events.append((1, "Text", f"{{\\an6\\pos({x:.0f},{y:.0f})\\c{YELLOW}&}}{points[row]}"))

    font = round(38 * u)
    return "\n".join([
        "[Script Info]",
        "ScriptType: v4.00+",
        f"PlayResX: {width}",
        f"PlayResY: {height}",
        "ScaledBorderAndShadow: yes",
        "",
        "[V4+ Styles]",
        "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, "
        "Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, "
        "MarginR, MarginV, Encoding",
        f"Style: Box,Arial,{font},{BOX},{BOX},{BOX},{BOX},0,0,0,0,100,100,0,0,1,0,0,7,0,0,0,1",
        f"Style: Text,Arial,{font},{WHITE},{WHITE},{BOX},{BOX},-1,0,0,0,100,100,0,0,1,0,0,7,0,0,0,1",
        "",
        "[Events]",
        "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text",
        *(f"Dialogue: {layer},0:00:00.00,9:59:59.99,{style},,0,0,0,,{text}"
          for layer, style, text in events),
        "",
    ])


def render_clip(video: Path, start: float, end: float, ass: str, out: Path) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        Path(tmp, "board.ass").write_text(ass, encoding="utf-8")
        subprocess.run(
            ["ffmpeg", "-nostdin", "-v", "error", "-y", "-ss", f"{start:.3f}", "-i", str(video.resolve()),
             "-t", f"{end - start:.3f}", "-map", "0:v:0", "-map", "0:a:0?", "-vf", "ass=board.ass",
             *ENCODE_ARGS, str(out.resolve())],
            cwd=tmp, check=True,
        )


def concat(clips: list[Path], out: Path) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        listing = Path(tmp, "clips.txt")
        listing.write_text("".join(f"file '{_concat_escape(c.resolve())}'\n" for c in clips), encoding="utf-8")
        subprocess.run(
            ["ffmpeg", "-nostdin", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(listing),
             "-c", "copy", "-movflags", "+faststart", str(out.resolve())],
            check=True,
        )


def _ass_text(name: str) -> str:
    return "".join(ch for ch in name if ch not in "{}\\")[:NAME_MAX_CHARS]


def _concat_escape(path: Path) -> str:
    return str(path).replace("'", "'\\''")
