package org.example.internservice.intern.repository;

import org.example.internservice.intern.entity.MentorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MentorProfileRepository extends JpaRepository<MentorProfile, Long> {

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Optional<MentorProfile> findByEmail(String email);

    Optional<MentorProfile> findByUserId(Long userId);

    @Query("SELECT m FROM MentorProfile m JOIN FETCH m.department ORDER BY m.createdAt DESC")
    List<MentorProfile> findAllWithDepartment();
}
