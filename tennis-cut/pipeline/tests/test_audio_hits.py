import numpy as np

from tennis import audio_hits

SR = 16000


def synth(duration, clicks, seed=0):
    """Background noise and a loud 200 Hz hum, plus sharp broadband clicks at the given times."""
    rng = np.random.default_rng(seed)
    t = np.arange(int(duration * SR)) / SR
    x = 0.005 * rng.standard_normal(len(t)) + 0.2 * np.sin(2 * np.pi * 200 * t)
    burst_t = np.arange(int(0.02 * SR)) / SR
    burst = 0.5 * rng.standard_normal(len(burst_t)) * np.exp(-burst_t / 0.003)
    for c in clicks:
        i = int(c * SR)
        x[i:i + len(burst)] += burst
    return (np.clip(x, -1, 1) * 32767).astype(np.int16)


def test_detects_clicks_at_the_right_time():
    truth = [1.0, 2.2, 3.1, 5.5, 8.0]
    hits = audio_hits.detect_hits(synth(10, truth), SR)
    assert len(hits) == len(truth)
    for h, t in zip(hits, truth):
        assert abs(h["t"] - t) <= 0.02
        assert 0 < h["conf"] <= 1


def test_low_frequency_thump_is_ignored():
    x = synth(6, [1.0, 4.0])
    t = np.arange(int(0.1 * SR)) / SR
    i = int(2.5 * SR)
    x[i:i + len(t)] += (8000 * np.sin(2 * np.pi * 150 * t)).astype(np.int16)
    hits = audio_hits.detect_hits(x, SR)
    assert [round(h["t"]) for h in hits] == [1, 4]


def test_min_gap_merges_close_transients():
    hits = audio_hits.detect_hits(synth(4, [1.0, 1.1, 3.0]), SR)
    assert len(hits) == 2


def test_srt_format():
    srt = audio_hits.to_srt([{"t": 3661.5, "conf": 0.8}])
    assert srt.startswith("1\n01:01:01,500 --> 01:01:01,750\nHIT 0.80\n")
