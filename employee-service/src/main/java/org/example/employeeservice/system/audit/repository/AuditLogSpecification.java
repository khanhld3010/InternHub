package org.example.employeeservice.system.audit.repository;

import jakarta.persistence.criteria.Predicate;
import org.example.employeeservice.system.audit.dto.request.AuditLogFilterRequest;
import org.example.employeeservice.system.audit.entity.AuditAction;
import org.example.employeeservice.system.audit.entity.AuditLog;
import org.example.employeeservice.system.audit.entity.AuditModule;
import org.example.employeeservice.system.audit.entity.AuditStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AuditLogSpecification {

    public static Specification<AuditLog> build(AuditLogFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter == null) {
                return cb.conjunction();
            }

            // Keyword search (username, description, endpoint)
            if (filter.getKeyword() != null && !filter.getKeyword().trim().isEmpty()) {
                String pattern = "%" + filter.getKeyword().trim().toLowerCase() + "%";
                Predicate keywordPredicate = cb.or(
                        cb.like(cb.lower(root.get("username")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(root.get("endpoint")), pattern)
                );
                predicates.add(keywordPredicate);
            }

            // Filter by module
            if (filter.getModule() != null && !filter.getModule().trim().isEmpty()) {
                try {
                    AuditModule moduleEnum = AuditModule.valueOf(filter.getModule().trim().toUpperCase());
                    predicates.add(cb.equal(root.get("module"), moduleEnum));
                } catch (IllegalArgumentException ignored) {
                }
            }

            // Filter by action
            if (filter.getAction() != null && !filter.getAction().trim().isEmpty()) {
                try {
                    AuditAction actionEnum = AuditAction.valueOf(filter.getAction().trim().toUpperCase());
                    predicates.add(cb.equal(root.get("action"), actionEnum));
                } catch (IllegalArgumentException ignored) {
                }
            }

            // Filter by status
            if (filter.getStatus() != null && !filter.getStatus().trim().isEmpty()) {
                try {
                    AuditStatus statusEnum = AuditStatus.valueOf(filter.getStatus().trim().toUpperCase());
                    predicates.add(cb.equal(root.get("status"), statusEnum));
                } catch (IllegalArgumentException ignored) {
                }
            }

            // Filter by username
            if (filter.getUsername() != null && !filter.getUsername().trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("username")), filter.getUsername().trim().toLowerCase()));
            }

            // Filter by date range (fromDate, toDate)
            LocalDateTime startDateTime = parseStartDateTime(filter.getFromDate());
            if (startDateTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDateTime));
            }

            LocalDateTime endDateTime = parseEndDateTime(filter.getToDate());
            if (endDateTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDateTime));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static LocalDateTime parseStartDateTime(String fromDateStr) {
        if (fromDateStr == null || fromDateStr.trim().isEmpty()) {
            return null;
        }
        try {
            if (fromDateStr.contains("T")) {
                return LocalDateTime.parse(fromDateStr);
            }
            LocalDate date = LocalDate.parse(fromDateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            return date.atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime parseEndDateTime(String toDateStr) {
        if (toDateStr == null || toDateStr.trim().isEmpty()) {
            return null;
        }
        try {
            if (toDateStr.contains("T")) {
                return LocalDateTime.parse(toDateStr);
            }
            LocalDate date = LocalDate.parse(toDateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            return date.atTime(LocalTime.MAX);
        } catch (Exception e) {
            return null;
        }
    }
}
