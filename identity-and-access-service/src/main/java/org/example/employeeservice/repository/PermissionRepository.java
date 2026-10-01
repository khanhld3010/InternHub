package org.example.employeeservice.repository;

import org.example.employeeservice.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Integer> {

    Optional<Permission> findByCode(String code);

    boolean existsByCode(String code);

    List<Permission> findByModuleOrderByCodeAsc(String module);

    List<Permission> findAllByOrderByModuleAscCodeAsc();

    List<Permission> findByCodeIn(Collection<String> codes);

    Set<Permission> findAllByCodeIn(Collection<String> codes);
}
