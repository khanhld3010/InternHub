package org.example.internservice.system.audit.entity;

public enum AuditAction {
    // Auth actions
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,

    // Intern actions
    CREATE_INTERN,
    UPDATE_INTERN,
    CHANGE_INTERN_STATUS,
    APPLY_INTERN,
    IMPORT_INTERNS_EXCEL,
    ASSIGN_MENTOR_TO_PROGRAM,

    // Document actions
    UPLOAD_DOCUMENT,
    REVIEW_DOCUMENT,
    DOWNLOAD_DOCUMENT,
    UPLOAD_CONTRACT,
    CONFIRM_CONTRACT,
    REJECT_CONTRACT,

    // System actions
    TRIGGER_BACKUP,
    DELETE_BACKUP,
    DOWNLOAD_BACKUP,

    // User actions
    TOGGLE_USER_STATUS,
    CREATE_USER,
    UPDATE_USER
}
