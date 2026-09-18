package org.example.employeeservice.intern.repository.specification;

import jakarta.persistence.criteria.Predicate;
import org.example.employeeservice.intern.dto.request.InternFilterRequest;
import org.example.employeeservice.intern.entity.InternProfile;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class InternProfileSpecification {

    private InternProfileSpecification() {
        // Utility class
    }

    public static Specification<InternProfile> getSpecification(InternFilterRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request == null) {
                return criteriaBuilder.conjunction();
            }

            // 1. Keyword search (fullName, email, phone, internCode) with OR
            if (StringUtils.hasText(request.getKeyword())) {
                String searchPattern = "%" + escapeWildcards(request.getKeyword().trim().toLowerCase()) + "%";
                Predicate fullNamePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("fullName")), searchPattern);
                Predicate emailPredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), searchPattern);
                Predicate phonePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("phone")), searchPattern);
                Predicate internCodePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("internCode")), searchPattern);

                predicates.add(criteriaBuilder.or(fullNamePredicate, emailPredicate, phonePredicate, internCodePredicate));
            }

            // 2. University filter (Partial/Contains match)
            if (StringUtils.hasText(request.getUniversity())) {
                String universityPattern = "%" + escapeWildcards(request.getUniversity().trim().toLowerCase()) + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("university")), universityPattern));
            }

            // 3. Major filter (Partial/Contains match)
            if (StringUtils.hasText(request.getMajor())) {
                String majorPattern = "%" + escapeWildcards(request.getMajor().trim().toLowerCase()) + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("major")), majorPattern));
            }

            // 4. Applied Position filter (Partial/Contains match)
            if (StringUtils.hasText(request.getAppliedPosition())) {
                String positionPattern = "%" + escapeWildcards(request.getAppliedPosition().trim().toLowerCase()) + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("appliedPosition")), positionPattern));
            }

            // 5. Status filter (Exact match)
            if (request.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), request.getStatus()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String escapeWildcards(String text) {
        return text.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
