package io.github.humphreymahlangu.yourvoice.exception;

public class InvalidVotingCredentialException extends RuntimeException {

    public InvalidVotingCredentialException() {
        super("Invalid or expired voting credential");
    }
}
