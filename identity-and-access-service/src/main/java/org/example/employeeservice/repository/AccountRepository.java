package org.example.employeeservice.repository;

import org.example.employeeservice.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Integer> {
    Optional<Account> findByUsername(String username);
    Optional<Account> findByUserId(Integer userId);
    boolean existsByUsername(String username);
    boolean existsByUserId(Integer userId);
    long countByRoleId(Integer roleId);

    @org.springframework.data.jpa.repository.Query("SELECT a.role.id, COUNT(a) FROM Account a " +
           "WHERE a.role.id IN :roleIds GROUP BY a.role.id")
    java.util.List<Object[]> countAccountsByRoleIdsGrouped(@org.springframework.data.repository.query.Param("roleIds") java.util.Collection<Integer> roleIds);
}

