package org.example.aiservice.modules.interview.repository;

import org.example.aiservice.modules.interview.entity.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {

    List<InterviewQuestion> findByQuestionSetIdOrderByDisplayOrderAsc(Long setId);
}
