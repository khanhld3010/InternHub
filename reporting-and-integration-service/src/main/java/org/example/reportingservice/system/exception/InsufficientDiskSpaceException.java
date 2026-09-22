package org.example.reportingservice.system.exception;

public class InsufficientDiskSpaceException extends RuntimeException {
    public InsufficientDiskSpaceException(String message) {
        super(message);
    }
}
