package org.example.internservice.intern.repository;

import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InternProfileRepository extends JpaRepository<InternProfile, Long>, JpaSpecificationExecutor<InternProfile> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    boolean existsByUserIdAndStatusIn(Long userId, List<InternStatus> statuses);

    boolean existsByEmailAndStatusIn(String email, List<InternStatus> statuses);

    boolean existsByUserIdAndProgramIdAndStatusIn(Long userId, Long programId, List<InternStatus> statuses);

    boolean existsByEmailAndProgramIdAndStatusIn(String email, Long programId, List<InternStatus> statuses);

    boolean existsByPhoneAndProgramIdAndStatusIn(String phone, Long programId, List<InternStatus> statuses);

    List<InternProfile> findAllByUserId(Long userId);

    List<InternProfile> findAllByUserIdOrderByIdDesc(Long userId);

    Optional<InternProfile> findByUserIdAndProgramId(Long userId, Long programId);

    default Optional<InternProfile> findByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        List<InternProfile> list = findAllByUserIdOrderByIdDesc(userId);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        return list.stream()
                .filter(p -> p.getStatus() == InternStatus.INTERNING)
                .findFirst()
                .or(() -> Optional.of(list.get(0)));
    }

    Optional<InternProfile> findByInternCode(String internCode);

    @org.springframework.data.jpa.repository.Query("SELECT MAX(i.internCode) FROM InternProfile i WHERE i.internCode LIKE CONCAT('INT-', :yearMonth, '-%')")
    String findMaxInternCodeByYearMonth(@org.springframework.data.repository.query.Param("yearMonth") String yearMonth);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    java.util.List<InternProfile> findByEmailStatusAndLastEmailSentAtBefore(String emailStatus, LocalDateTime threshold);

    long countByProgramId(Long programId);

    long countByProgramIdAndStatusIn(Long programId, java.util.Collection<org.example.internservice.intern.entity.enums.InternStatus> statuses);

    boolean existsByProgramIdAndStatusIn(Long programId, java.util.Collection<org.example.internservice.intern.entity.enums.InternStatus> statuses);

    java.util.List<InternProfile> findByProgramId(Long programId);
    java.util.List<InternProfile> findByProgramIdAndStatusIn(Long programId, java.util.Collection<org.example.internservice.intern.entity.enums.InternStatus> statuses);

    long countByMentorIdAndStatus(Long mentorId, InternStatus status);

    java.util.List<InternProfile> findByProgramIdAndStatusAndMentorIdIsNotNull(Long programId, InternStatus status);

    java.util.List<InternProfile> findByMentorId(Long mentorId);

    java.util.List<InternProfile> findByGroupId(Long groupId);

    long countByGroupId(Long groupId);

    java.util.List<InternProfile> findByProgramIdAndGroupIsNull(Long programId);
    java.util.List<InternProfile> findByMentorIdIn(java.util.Collection<Long> mentorIds);

    List<InternProfile> findAllByEmailIn(java.util.Collection<String> emails);
    List<InternProfile> findAllByPhoneIn(java.util.Collection<String> phones);
    List<InternProfile> findAllByProgramIdAndEmailIn(Long programId, java.util.Collection<String> emails);
    List<InternProfile> findAllByProgramIdAndPhoneIn(Long programId, java.util.Collection<String> phones);
}

