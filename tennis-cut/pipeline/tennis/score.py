"""Tennis scoring state machine: pure functions over an immutable Score.

A tiebreak (and a match tiebreak) counts as one game for server rotation and
change of ends, which is what the ITF rules amount to.
"""

from __future__ import annotations

from dataclasses import dataclass, replace

PLAYERS = ("A", "B")
FINAL_SET_RULES = ("tiebreak", "match_tiebreak", "advantage")
POINT_NAMES = ("0", "15", "30", "40")


@dataclass(frozen=True)
class Format:
    sets: int = 3  # best of
    no_ad: bool = False
    final_set: str = "tiebreak"

    def __post_init__(self) -> None:
        if self.sets not in (1, 3, 5):
            raise ValueError(f"sets must be 1, 3 or 5, got {self.sets}")
        if self.final_set not in FINAL_SET_RULES:
            raise ValueError(f"final_set must be one of {FINAL_SET_RULES}, got {self.final_set!r}")


@dataclass(frozen=True)
class Score:
    sets: tuple[tuple[int, int], ...] = ()  # completed sets; a match tiebreak is stored as its points
    games: tuple[int, int] = (0, 0)
    points: tuple[int, int] = (0, 0)  # current game or tiebreak
    game_server: str = "A"  # whose turn it is in the game rotation
    a_near: bool = True
    winner: str | None = None


def initial(first_server: str, a_starts_near: bool) -> Score:
    if first_server not in PLAYERS:
        raise ValueError(f"first_server must be A or B, got {first_server!r}")
    return Score(game_server=first_server, a_near=a_starts_near)


def is_match_tiebreak(score: Score, fmt: Format) -> bool:
    return len(score.sets) == fmt.sets - 1 and fmt.final_set == "match_tiebreak"


def in_tiebreak(score: Score, fmt: Format) -> bool:
    deciding = len(score.sets) == fmt.sets - 1
    if deciding and fmt.final_set == "match_tiebreak":
        return True
    if deciding and fmt.final_set == "advantage":
        return False
    return score.games == (6, 6)


def server(score: Score, fmt: Format) -> str:
    """Who serves the next point. In a tiebreak: 1 point, then 2 each."""
    if not in_tiebreak(score, fmt):
        return score.game_server
    n = sum(score.points)
    if n == 0 or (n - 1) // 2 % 2 == 1:
        return score.game_server
    return _other(score.game_server)


def apply_point(score: Score, fmt: Format, winner: str) -> Score:
    if score.winner is not None:
        raise ValueError("match is already over")
    w = PLAYERS.index(winner)
    pts = _bump(score.points, w)
    tiebreak = in_tiebreak(score, fmt)
    if tiebreak:
        target = 10 if is_match_tiebreak(score, fmt) else 7
        game_won = pts[w] >= target and pts[w] - pts[1 - w] >= 2
    else:
        game_won = pts[w] >= 4 and (fmt.no_ad or pts[w] - pts[1 - w] >= 2)

    if not game_won:
        swap = tiebreak and sum(pts) % 6 == 0
        return replace(score, points=pts, a_near=score.a_near ^ swap)

    match_tb = is_match_tiebreak(score, fmt)
    games = _bump(score.games, w)
    games_in_set = 1 if match_tb else sum(games)
    a_near = score.a_near ^ (games_in_set % 2 == 1)
    next_server = _other(score.game_server)
    set_over = tiebreak or (games[w] >= 6 and games[w] - games[1 - w] >= 2)
    if not set_over:
        return replace(score, games=games, points=(0, 0), game_server=next_server, a_near=a_near)

    sets = score.sets + ((pts if match_tb else games),)
    sets_won = sum(1 for s in sets if s[w] > s[1 - w])
    return Score(
        sets=sets,
        game_server=next_server,
        a_near=a_near,
        winner=PLAYERS[w] if sets_won == fmt.sets // 2 + 1 else None,
    )


def point_labels(score: Score, fmt: Format) -> tuple[str, str]:
    a, b = score.points
    if in_tiebreak(score, fmt):
        return str(a), str(b)
    if a >= 3 and b >= 3 and a != b:
        return ("AD", "40") if a > b else ("40", "AD")
    return POINT_NAMES[min(a, 3)], POINT_NAMES[min(b, 3)]


def display(score: Score, fmt: Format) -> dict:
    """The `score_after` block of session.json."""
    return {
        "sets": [list(s) for s in score.sets],
        "games": list(score.games),
        "points": "" if score.winner else "-".join(point_labels(score, fmt)),
    }


def _bump(pair: tuple[int, int], i: int) -> tuple[int, int]:
    return (pair[0] + 1, pair[1]) if i == 0 else (pair[0], pair[1] + 1)


def _other(player: str) -> str:
    return "B" if player == "A" else "A"
