package org.example.internservice.meeting.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "meeting_schedules", indexes = {
        @Index(name = "idx_meeting_intern_user", columnList = "intern_user_id"),
        @Index(name = "idx_meeting_creator", columnList = "creator_id"),
        @Index(name = "idx_meeting_start_time", columnList = "start_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_type", nullable = false, length = 20)
    @Builder.Default
    private MeetingType meetingType = MeetingType.ONLINE;

    @Column(name = "location_or_url", length = 500)
    private String locationOrUrl;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "creator_name", length = 100)
    private String creatorName;

    @Column(name = "creator_role", length = 50)
    private String creatorRole;

    @Column(name = "intern_profile_id")
    private Long internProfileId;

    @Column(name = "intern_user_id", nullable = false)
    private Long internUserId;

    @Column(name = "intern_name", length = 100)
    private String internName;

    @Column(name = "intern_email", length = 100)
    private String internEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MeetingStatus status = MeetingStatus.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    @Builder.Default
    private MeetingProviderType provider = MeetingProviderType.JITSI;

    @Column(name = "join_url", length = 500)
    private String joinUrl;

    @Column(name = "external_event_id", length = 255)
    private String externalEventId;

    @Column(name = "sequence_number", nullable = false)
    @Builder.Default
    private Integer sequenceNumber = 0;

    @Column(name = "reminder_sent", nullable = false)
    @Builder.Default
    private Boolean reminderSent = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum MeetingType {
        ONLINE,
        OFFLINE
    }

    public enum MeetingProviderType {
        CUSTOM_MANUAL,
        GOOGLE_MEET,
        JITSI
    }

    public enum MeetingStatus {
        SCHEDULED,
        UPDATED,
        CANCELLED,
        COMPLETED
    }
}

