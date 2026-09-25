package org.example.internservice.program.repository;

import org.example.internservice.program.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByStatus(String status);

    Optional<Department> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByName(String name);
}
