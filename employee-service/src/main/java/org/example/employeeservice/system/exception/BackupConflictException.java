package org.example.employeeservice.system.exception;

public class BackupConflictException extends RuntimeException {
    public BackupConflictException(String message) {
        super(message);
    }
}
