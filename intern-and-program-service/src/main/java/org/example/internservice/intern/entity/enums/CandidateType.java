package org.example.internservice.intern.entity.enums;

import lombok.Getter;

@Getter
public enum CandidateType {
    UNIVERSITY("Sinh viên theo trường liên kết"),
    FREE_APPLICANT("Ứng viên tự do");

    private final String displayName;

    CandidateType(String displayName) {
        this.displayName = displayName;
    }
}
