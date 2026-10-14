package io.github.humphreymahlangu.votetrust.exception;

public class InvalidBootstrapTokenException extends RuntimeException {

    public InvalidBootstrapTokenException() {
        super("Invalid admin bootstrap token");
    }
}
