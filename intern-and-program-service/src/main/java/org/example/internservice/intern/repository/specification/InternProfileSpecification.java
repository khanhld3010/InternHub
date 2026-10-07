package org.example.internservice.intern.repository.specification;

import jakarta.persistence.criteria.Predicate;
import org.example.internservice.intern.dto.request.InternFilterRequest;
import org.example.internservice.intern.entity.InternProfile;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class InternProfileSpecification {

    private InternProfileSpecification() {
        // Utility class
    }

    public static Specification<InternProfile> getSpecification(InternFilterRequest request) {
        return getSpecification(request, (List<Long>) null);
    }

    public static Specification<InternProfile> getSpecification(InternFilterRequest request, Long enforceMentorId) {
        return getSpecification(request, enforceMentorId != null ? List.of(enforceMentorId) : null);
    }

    public static Specification<InternProfile> getSpecification(InternFilterRequest request, List<Long> enforceMentorIds) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Ràng buộc bảo mật tối thượng: Nếu có danh sách enforceMentorIds thì bắt buộc phải khớp một trong các mentorId
            if (enforceMentorIds != null && !enforceMentorIds.isEmpty()) {
                if (enforceMentorIds.size() == 1) {
                    predicates.add(criteriaBuilder.equal(root.get("mentorId"), enforceMentorIds.get(0)));
                } else {
                    predicates.add(root.get("mentorId").in(enforceMentorIds));
                }
            }

            if (request == null) {
                return predicates.isEmpty() ? criteriaBuilder.conjunction() : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
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

            // 6. Program filter (Exact match)
            if (request.getProgramId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("program").get("id"), request.getProgramId()));
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
