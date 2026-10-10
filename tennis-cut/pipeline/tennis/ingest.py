"""Read video metadata and audio with ffprobe/ffmpeg. All times are seconds from the file start (PTS)."""

from __future__ import annotations

import json
import subprocess
from fractions import Fraction
from pathlib import Path

import numpy as np

AUDIO_SR = 16000


def probe(video: Path) -> dict:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-print_format", "json", "-show_streams", "-show_format", str(video)],
        capture_output=True, text=True, check=True,
    )
    info = json.loads(out.stdout)
    v = next(s for s in info["streams"] if s["codec_type"] == "video")
    width, height = v["width"], v["height"]
    rotation = next((int(d["rotation"]) for d in v.get("side_data_list", []) if "rotation" in d), 0)
    if abs(rotation) in (90, 270):  # ffmpeg auto-rotates on decode, so report the upright size
        width, height = height, width
    return {
        "fps": round(float(Fraction(v["avg_frame_rate"])), 3),
        "width": width,
        "height": height,
        "duration": float(info["format"]["duration"]),
    }


def load_audio(video: Path) -> np.ndarray:
    """Mono int16 PCM at AUDIO_SR."""
    out = subprocess.run(
        ["ffmpeg", "-nostdin", "-v", "error", "-i", str(video), "-vn", "-ac", "1", "-ar", str(AUDIO_SR),
         "-f", "s16le", "-"],
        capture_output=True, check=True,
    )
    return np.frombuffer(out.stdout, dtype=np.int16)
