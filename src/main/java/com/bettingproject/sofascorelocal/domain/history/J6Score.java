package com.bettingproject.sofascorelocal.domain.history;

public record J6Score(int home, int away) {

    public J6Score {
        if (home < 0 || home > 99 || away < 0 || away > 99) {
            throw new IllegalArgumentException("score values must be between 0 and 99");
        }
    }

    public String label() {
        return home + "–" + away;
    }
}
