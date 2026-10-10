import pytest

from tennis import score as sc

BO3 = sc.Format(sets=3)


def play(state, fmt, winners):
    for w in winners:
        state = sc.apply_point(state, fmt, w)
    return state


def hold(player):
    return player * 4


SIX_ALL = (hold("A") + hold("B")) * 6


def test_game_progression_and_labels():
    s = sc.initial("A", True)
    labels = []
    for w in "AABAA":
        s = sc.apply_point(s, BO3, w)
        labels.append(sc.display(s, BO3)["points"])
    assert labels == ["15-0", "30-0", "30-15", "40-15", "0-0"]
    assert s.games == (1, 0)


def test_deuce_and_advantage():
    s = play(sc.initial("A", True), BO3, "AAABBB")
    assert sc.display(s, BO3)["points"] == "40-40"
    s = sc.apply_point(s, BO3, "B")
    assert sc.display(s, BO3)["points"] == "40-AD"
    s = sc.apply_point(s, BO3, "A")
    assert sc.display(s, BO3)["points"] == "40-40"
    s = play(s, BO3, "AA")
    assert s.games == (1, 0) and s.points == (0, 0)


def test_no_ad_deciding_point():
    fmt = sc.Format(sets=3, no_ad=True)
    s = play(sc.initial("A", True), fmt, "AAABBB")
    s = sc.apply_point(s, fmt, "B")
    assert s.games == (0, 1)


def test_server_alternates_each_game():
    s = sc.initial("B", True)
    assert sc.server(s, BO3) == "B"
    s = play(s, BO3, hold("B"))
    assert sc.server(s, BO3) == "A"


def test_set_needs_two_game_lead():
    s = play(sc.initial("A", True), BO3, hold("A") * 5 + hold("B") * 5)
    assert s.games == (5, 5)
    s = play(s, BO3, hold("A"))
    assert s.games == (6, 5) and s.sets == ()
    s = play(s, BO3, hold("A"))
    assert s.sets == ((7, 5),) and s.games == (0, 0)


def test_tiebreak_at_six_all():
    s = play(sc.initial("A", True), BO3, SIX_ALL)
    assert s.games == (6, 6) and sc.in_tiebreak(s, BO3)
    # Server rotation in the tiebreak: whoever's turn it is serves 1, then 2 each.
    servers = []
    for _ in range(7):
        servers.append(sc.server(s, BO3))
        s = sc.apply_point(s, BO3, "A")
    assert servers == ["A", "B", "B", "A", "A", "B", "B"]
    assert s.sets == ((7, 6),)


def test_tiebreak_labels_and_win_by_two():
    s = play(sc.initial("A", True), BO3, SIX_ALL)
    s = play(s, BO3, "AB" * 6)
    assert sc.display(s, BO3)["points"] == "6-6"
    s = play(s, BO3, "A")
    assert s.sets == ()
    s = play(s, BO3, "A")
    assert s.sets == ((7, 6),)


def test_after_tiebreak_set_first_receiver_serves():
    # A serves game 1; 12 games alternate, so A starts the tiebreak and B serves next set.
    s = play(sc.initial("A", True), BO3, SIX_ALL)
    assert sc.server(s, BO3) == "A"
    s = play(s, BO3, "A" * 7)
    assert sc.server(s, BO3) == "B"


def test_match_tiebreak_final_set():
    fmt = sc.Format(sets=3, final_set="match_tiebreak")
    s = play(sc.initial("A", True), fmt, SIX_ALL)
    s = play(s, fmt, "A" * 7)  # set 1 to A, 7-6
    s = play(s, fmt, hold("B") * 6)  # set 2 to B, 0-6
    assert sc.is_match_tiebreak(s, fmt)
    s = play(s, fmt, "AB" * 9)
    assert sc.display(s, fmt)["points"] == "9-9"
    s = play(s, fmt, "BAAA")
    assert s.winner == "A"
    assert sc.display(s, fmt) == {"sets": [[7, 6], [0, 6], [12, 10]], "games": [0, 0], "points": ""}


def test_advantage_final_set_has_no_tiebreak():
    fmt = sc.Format(sets=3, final_set="advantage")
    s = play(sc.initial("A", True), fmt, hold("A") * 6 + hold("B") * 6)  # one set each
    s = play(s, fmt, SIX_ALL)
    assert s.games == (6, 6) and not sc.in_tiebreak(s, fmt)
    s = play(s, fmt, hold("A"))
    assert s.games == (7, 6) and s.winner is None
    s = play(s, fmt, hold("A"))
    assert s.sets[-1] == (8, 6) and s.winner == "A"


def test_best_of_three_ends_match():
    s = play(sc.initial("A", True), BO3, "A" * 4 * 12)
    assert s.sets == ((6, 0), (6, 0)) and s.winner == "A"
    with pytest.raises(ValueError):
        sc.apply_point(s, BO3, "A")


def test_ends_change_after_odd_games():
    s = sc.initial("A", True)
    nears = []
    for _ in range(4):
        s = play(s, BO3, "A" * 4)
        nears.append(s.a_near)
    assert nears == [False, False, True, True]


def test_ends_at_set_boundary():
    # Even set (6-0): no change at set end, change after game 1 of the next set.
    s = play(sc.initial("A", True), BO3, hold("A") * 5)
    near = s.a_near
    s = play(s, BO3, hold("A"))
    assert s.sets == ((6, 0),) and s.a_near is near
    s = play(s, BO3, hold("A"))
    assert s.a_near is not near
    # Odd set (6-1): change at set end and again after game 1 of the next set.
    s = play(sc.initial("A", True), BO3, hold("B") + hold("A") * 5)
    near = s.a_near
    s = play(s, BO3, hold("A"))
    assert s.sets == ((6, 1),) and s.a_near is not near
    s = play(s, BO3, hold("A"))
    assert s.a_near is near


def test_ends_change_every_six_tiebreak_points():
    s = play(sc.initial("A", True), BO3, SIX_ALL)
    near = s.a_near
    s = play(s, BO3, "AB" * 2 + "A")
    assert s.a_near is near
    s = play(s, BO3, "B")
    assert s.a_near is not near
    s = play(s, BO3, "AB" * 3)
    assert s.a_near is near


def test_format_validation():
    with pytest.raises(ValueError):
        sc.Format(sets=2)
    with pytest.raises(ValueError):
        sc.Format(final_set="super")
