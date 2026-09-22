package org.example.reportingservice.system.audit.entity;

public enum AuditAction {
    // Auth actions
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,

    // Intern actions
    CREATE_INTERN,
    UPDATE_INTERN,
    CHANGE_INTERN_STATUS,

    // Document actions
    UPLOAD_DOCUMENT,
    REVIEW_DOCUMENT,
    DOWNLOAD_DOCUMENT,

    // System actions
    TRIGGER_BACKUP,
    DELETE_BACKUP,
    DOWNLOAD_BACKUP,

    // User actions
    TOGGLE_USER_STATUS,
    CREATE_USER,
    UPDATE_USER
}
