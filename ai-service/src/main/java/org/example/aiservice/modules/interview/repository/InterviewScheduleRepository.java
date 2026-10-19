package org.example.aiservice.modules.interview.repository;

import org.example.aiservice.modules.interview.entity.InterviewSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewScheduleRepository extends JpaRepository<InterviewSchedule, Long> {

    Page<InterviewSchedule> findByProgramId(Long programId, Pageable pageable);

    List<InterviewSchedule> findByCreatedByHrId(String createdByHrId);
}
