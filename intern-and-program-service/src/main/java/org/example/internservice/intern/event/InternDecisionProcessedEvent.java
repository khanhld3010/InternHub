package org.example.internservice.intern.event;

import lombok.Getter;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

@Getter
public class InternDecisionProcessedEvent extends ApplicationEvent {

    private final Long internProfileId;
    private final String internCode;
    private final String fullName;
    private final String email;
    private final InternStatus decision;
    private final String rejectionReason;
    private final String reviewedBy;
    private final LocalDateTime reviewedAt;
    private final String appliedPosition;
    private final java.time.LocalDate startDate;

    public InternDecisionProcessedEvent(Object source, InternProfile profile) {
        super(source);
        this.internProfileId = profile.getId();
        this.internCode = profile.getInternCode();
        this.fullName = profile.getFullName();
        this.email = profile.getEmail();
        this.decision = profile.getStatus();
        this.rejectionReason = profile.getRejectionReason();
        this.reviewedBy = profile.getReviewedBy();
        this.reviewedAt = profile.getReviewedAt();
        this.appliedPosition = profile.getAppliedPosition();
        this.startDate = profile.getStartDate();
    }
}
