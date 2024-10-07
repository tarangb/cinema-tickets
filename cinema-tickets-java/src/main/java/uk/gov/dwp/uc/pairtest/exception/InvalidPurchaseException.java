package uk.gov.dwp.uc.pairtest.exception;

public class InvalidPurchaseException extends RuntimeException {

    // Constructor that accepts an error message
    public InvalidPurchaseException(String message) {
        super(message);  // Pass the message to the superclass constructor
    }
}
