package io.github.humphreymahlangu.votetrust.exception;

public class InvalidVotingCredentialException extends RuntimeException {

    public InvalidVotingCredentialException() {
        super("Invalid or expired voting credential");
    }
}
