package org.example.internservice.intern.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class InternMentorAssignedEvent extends ApplicationEvent {

    private final Long internProfileId;
    private final String internCode;
    private final String internName;
    private final String internEmail;
    private final String programName;
    private final String appliedPosition;

    private final String eventType; // ASSIGNED, REPLACED, REVOKED

    private final Long newMentorId;
    private final String newMentorName;
    private final String newMentorEmail;

    private final Long oldMentorId;
    private final String oldMentorName;
    private final String oldMentorEmail;

    private final String reason;
    private final String notes;
    private final String actorUsername;

    public InternMentorAssignedEvent(
            Object source,
            Long internProfileId,
            String internCode,
            String internName,
            String internEmail,
            String programName,
            String appliedPosition,
            String eventType,
            Long newMentorId,
            String newMentorName,
            String newMentorEmail,
            Long oldMentorId,
            String oldMentorName,
            String oldMentorEmail,
            String reason,
            String notes,
            String actorUsername
    ) {
        super(source);
        this.internProfileId = internProfileId;
        this.internCode = internCode;
        this.internName = internName;
        this.internEmail = internEmail;
        this.programName = programName;
        this.appliedPosition = appliedPosition;
        this.eventType = eventType;
        this.newMentorId = newMentorId;
        this.newMentorName = newMentorName;
        this.newMentorEmail = newMentorEmail;
        this.oldMentorId = oldMentorId;
        this.oldMentorName = oldMentorName;
        this.oldMentorEmail = oldMentorEmail;
        this.reason = reason;
        this.notes = notes;
        this.actorUsername = actorUsername;
    }
}
