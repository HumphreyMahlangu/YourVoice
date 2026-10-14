package io.github.humphreymahlangu.yourvoice.exception;

public class InvalidBootstrapTokenException extends RuntimeException {

    public InvalidBootstrapTokenException() {
        super("Invalid admin bootstrap token");
    }
}
