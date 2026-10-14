package io.github.humphreymahlangu.votetrust.exception;

public class LoginThrottledException extends RuntimeException {

    private final int retryAfterSeconds;

    public LoginThrottledException(int retryAfterSeconds) {
        super("Too many failed sign-in attempts. Try again in " + Math.max(1, (retryAfterSeconds + 59) / 60) + " minute(s).");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
