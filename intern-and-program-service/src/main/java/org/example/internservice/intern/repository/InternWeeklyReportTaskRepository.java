package org.example.internservice.intern.repository;

import org.example.internservice.intern.entity.InternWeeklyReportTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InternWeeklyReportTaskRepository extends JpaRepository<InternWeeklyReportTask, Long> {

    List<InternWeeklyReportTask> findByReportId(Long reportId);
}
