package org.example.internservice.program.repository;

import org.example.internservice.program.entity.ProgramMentor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramMentorRepository extends JpaRepository<ProgramMentor, Long> {

    boolean existsByProgramIdAndMentorId(Long programId, Long mentorId);

    @Query("SELECT CASE WHEN COUNT(pm) > 0 THEN true ELSE false END FROM ProgramMentor pm WHERE pm.program.id = :programId AND (pm.mentor.id = :mentorIdentifier OR pm.mentor.userId = :mentorIdentifier)")
    boolean existsByProgramIdAndMentorIdentifier(@Param("programId") Long programId, @Param("mentorIdentifier") Long mentorIdentifier);

    @Query("SELECT pm FROM ProgramMentor pm JOIN FETCH pm.program p LEFT JOIN FETCH p.department WHERE pm.mentor.id = :mentorId OR pm.mentor.userId = :mentorId")
    List<ProgramMentor> findByMentorIdentifierWithProgram(@Param("mentorId") Long mentorId);

    List<ProgramMentor> findByProgramId(Long programId);

    Optional<ProgramMentor> findByProgramIdAndMentorId(Long programId, Long mentorId);

    @Query("SELECT pm.program.id, COUNT(pm) FROM ProgramMentor pm WHERE pm.program.id IN :programIds GROUP BY pm.program.id")
    List<Object[]> countMentorsByProgramIds(@Param("programIds") Collection<Long> programIds);
}
