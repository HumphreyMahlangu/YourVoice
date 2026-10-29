package io.github.humphreymahlangu.yourvoice.exception;

public class VerificationUnavailableException extends RuntimeException {
    public VerificationUnavailableException() { super("The fictional verification source is unavailable. Please try again later."); }
}
