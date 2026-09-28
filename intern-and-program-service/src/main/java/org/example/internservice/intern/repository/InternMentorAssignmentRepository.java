package org.example.internservice.intern.repository;

import org.example.internservice.intern.entity.InternMentorAssignment;
import org.example.internservice.intern.entity.enums.MentorAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InternMentorAssignmentRepository extends JpaRepository<InternMentorAssignment, Long> {

    List<InternMentorAssignment> findByInternIdOrderByAssignedAtDesc(Long internId);

    Optional<InternMentorAssignment> findByInternIdAndStatus(Long internId, MentorAssignmentStatus status);

    List<InternMentorAssignment> findByMentorIdAndStatus(Long mentorId, MentorAssignmentStatus status);

    long countByMentorIdAndStatus(Long mentorId, MentorAssignmentStatus status);
}
