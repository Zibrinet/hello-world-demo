"""Racket-hit candidates from audio: band-limited spectral flux plus adaptive peak picking.

v1 (BRIEF §5.3). It also fires on bounces, squeaks and neighbouring courts; the
M2 classifier trained on labelled windows is what rejects those.
"""

from __future__ import annotations

import numpy as np
from numpy.lib.stride_tricks import sliding_window_view
from scipy.ndimage import median_filter
from scipy.signal import find_peaks

N_FFT = 512  # 32 ms at 16 kHz
HOP = 160  # 10 ms
BAND_HZ = (1000.0, 8000.0)
LOG_GAIN = 100.0
BLOCK_FRAMES = 4096  # bounds memory on 90-minute recordings
MEDIAN_WINDOW_S = 2.0
MIN_GAP_S = 0.35
DEFAULT_THRESHOLD = 5.0  # robust z-score above the local median


def onset_envelope(x: np.ndarray, sr: int) -> np.ndarray:
    """Mean positive change in log magnitude across BAND_HZ, one value per HOP samples."""
    if len(x) < N_FFT:
        return np.zeros(0, np.float32)
    scale = 1.0 / 32768 if np.issubdtype(x.dtype, np.integer) else 1.0
    window = np.hanning(N_FFT).astype(np.float32)
    freqs = np.fft.rfftfreq(N_FFT, 1 / sr)
    band = (freqs >= BAND_HZ[0]) & (freqs <= BAND_HZ[1])
    n_frames = 1 + (len(x) - N_FFT) // HOP
    env = np.zeros(n_frames, np.float32)
    prev = None
    for s in range(0, n_frames, BLOCK_FRAMES):
        e = min(n_frames, s + BLOCK_FRAMES)
        seg = x[s * HOP:(e - 1) * HOP + N_FFT].astype(np.float32) * scale
        frames = sliding_window_view(seg, N_FFT)[::HOP]
        mag = np.log1p(LOG_GAIN * np.abs(np.fft.rfft(frames * window, axis=1)[:, band]))
        stacked = np.vstack([mag[:1] if prev is None else prev, mag])
        env[s:e] = np.maximum(np.diff(stacked, axis=0), 0).mean(axis=1)
        prev = mag[-1:]
    return env


def detect_hits(x: np.ndarray, sr: int, threshold: float = DEFAULT_THRESHOLD) -> list[dict]:
    """Returns [{"t": seconds, "conf": 0..1}], at most one per MIN_GAP_S."""
    env = onset_envelope(x, sr)
    if len(env) == 0:
        return []
    frame_s = HOP / sr
    local = median_filter(env, size=int(MEDIAN_WINDOW_S / frame_s) | 1, mode="reflect")
    sigma = 1.4826 * float(np.median(np.abs(env - local))) or 1e-9
    z = (env - local) / sigma
    peaks, _ = find_peaks(z, height=threshold, distance=max(1, round(MIN_GAP_S / frame_s)))
    conf = 1 - np.exp(-(z[peaks] - threshold) / threshold)
    # Frame centre: on synthetic clicks this lands within ~5 ms of the onset.
    t = (peaks * HOP + N_FFT / 2) / sr
    return [{"t": round(float(ti), 3), "conf": round(float(c), 2)} for ti, c in zip(t, conf)]


def to_srt(hits: list[dict], show_s: float = 0.25) -> str:
    """Subtitle track that flashes HIT at each detection, for eyeballing in a video player."""
    return "\n".join(
        f"{i}\n{_srt_time(h['t'])} --> {_srt_time(h['t'] + show_s)}\nHIT {h['conf']:.2f}\n"
        for i, h in enumerate(hits, 1)
    )


def _srt_time(t: float) -> str:
    ms = round(t * 1000)
    return f"{ms // 3_600_000:02d}:{ms // 60_000 % 60:02d}:{ms // 1000 % 60:02d},{ms % 1000:03d}"
