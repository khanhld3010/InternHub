package org.example.internservice.leave.repository.specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.leave.dto.request.LeaveRequestFilterRequest;
import org.example.internservice.leave.entity.LeaveRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LeaveRequestSpecification {

    private LeaveRequestSpecification() {
        // Utility class
    }

    public static Specification<LeaveRequest> getSpecification(LeaveRequestFilterRequest filter, Collection<Long> allowedInternIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Ràng buộc bảo mật phạm vi: Chỉ cho phép truy vấn các TTS thuộc allowedInternIds (nếu khác null)
            if (allowedInternIds != null) {
                if (allowedInternIds.isEmpty()) {
                    return cb.disjunction();
                }
                predicates.add(root.get("intern").get("id").in(allowedInternIds));
            }

            if (filter == null) {
                return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
            }

            // 2. Lọc theo trạng thái đơn
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            // 3. Lọc theo loại nghỉ phép
            if (filter.getLeaveType() != null) {
                predicates.add(cb.equal(root.get("leaveType"), filter.getLeaveType()));
            }

            // 4. Lọc theo thực tập sinh cụ thể
            if (filter.getInternId() != null) {
                predicates.add(cb.equal(root.get("intern").get("id"), filter.getInternId()));
            }

            // 5. Lọc theo khoảng ngày nghỉ phép
            if (filter.getStartDate() != null && filter.getEndDate() != null) {
                predicates.add(cb.and(
                        cb.lessThanOrEqualTo(root.get("startDate"), filter.getEndDate()),
                        cb.greaterThanOrEqualTo(root.get("endDate"), filter.getStartDate())
                ));
            } else if (filter.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), filter.getStartDate()));
            } else if (filter.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), filter.getEndDate()));
            }

            // 6. Tìm kiếm theo từ khóa (Mã TTS, Họ tên, Email)
            if (StringUtils.hasText(filter.getKeyword())) {
                String pattern = "%" + filter.getKeyword().trim().toLowerCase() + "%";
                Join<LeaveRequest, InternProfile> internJoin = root.join("intern");
                Predicate nameMatch = cb.like(cb.lower(internJoin.get("fullName")), pattern);
                Predicate codeMatch = cb.like(cb.lower(internJoin.get("internCode")), pattern);
                Predicate emailMatch = cb.like(cb.lower(internJoin.get("email")), pattern);
                predicates.add(cb.or(nameMatch, codeMatch, emailMatch));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
