package io.github.humphreymahlangu.votetrust.entity;

public enum ContestOptionType {
    PARTY(true),
    INDEPENDENT_CANDIDATE(true),
    BLANK_BALLOT(false),
    SPOILT_BALLOT(false);

    private final boolean validVote;

    ContestOptionType(boolean validVote) {
        this.validVote = validVote;
    }

    public boolean isValidVote() {
        return validVote;
    }

    public boolean isNonValidBallot() {
        return !validVote;
    }
}
