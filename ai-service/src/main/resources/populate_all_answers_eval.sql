DELETE FROM interview_answer_evaluations WHERE evaluation_id = 2;

INSERT INTO interview_answer_evaluations 
(evaluation_id, question_id, score, hr_override_score, weight, technical_accuracy_score, optimization_score, articulation_score, edge_cases_score, evidence_quote, rationale, suggested_solution, is_flagged_suspicious)
VALUES 
(2, 16, 10.00, NULL, 1.00, 10.00, 10.00, 10.00, 10.00, 'chịu chết', 'Ứng viên không trả lời được vòng đời Bean và sự khác biệt giữa @Component và @Service.', 'Cần nắm rõ các giai đoạn khởi tạo Bean (@PostConstruct, IoC Container) và @Service là chuyên biệt hóa logic nghiệp vụ của @Component.', 0),
(2, 17, 20.00, NULL, 1.00, 20.00, 20.00, 20.00, 20.00, 'Quá khó', 'Ứng viên chưa nắm được bản chất truy vấn N+1 trong JPA/Hibernate.', 'Nên giải thích phương án JOIN FETCH, @EntityGraph hoặc DTO Projection để giải quyết truy vấn N+1.', 0),
(2, 18, 15.00, NULL, 1.00, 15.00, 15.00, 15.00, 15.00, 'Khó khăn là khắn kho', 'Ứng viên không mô tả được kiến trúc hệ thống tự thiết kế hay sự cố thực tế.', 'Cần trình bày cấu trúc module, luồng dữ liệu và giải pháp khắc phục sự cố kỹ thuật trong dự án.', 0),
(2, 19, 10.00, NULL, 1.00, 10.00, 10.00, 10.00, 10.00, 'Chưa', 'Ứng viên chưa có kiến thức hay trải nghiệm về CI/CD và Docker.', 'Cần tìm hiểu quy trình container hóa với Dockerfile và các giai đoạn pipeline CI/CD (lint, test, build, deploy staging).', 0),
(2, 20, 5.00, NULL, 1.00, 5.00, 5.00, 5.00, 5.00, 'Xóa luôn', 'Thái độ xử lý công việc chưa chuyên nghiệp khi gặp công nghệ mới và áp lực thời gian.', 'Cần chủ động tra cứu tài liệu chính thức (Official Docs), lên roadmap 3 ngày và trao đổi hỗ trợ từ Mentor/Lead.', 0),
(2, 21, 5.00, NULL, 1.00, 5.00, 5.00, 5.00, 5.00, 'Tôi đánh nhau', 'Kỹ năng giải quyết xung đột bất đồng ý kiến mang tính tiêu cực, không phù hợp văn hóa làm việc nhóm.', 'Cần đưa ra phân tích khách quan dựa trên số liệu kỹ thuật, benchmark và thảo luận văn minh để đạt được đồng thuận.', 0);

UPDATE interview_evaluations 
SET 
  total_score = 10.83,
  hr_final_total_score = 10.83,
  model_name = 'gemini-3.5-flash-lite',
  ai_summary = 'Ứng viên hoàn thành 6/6 câu hỏi nhưng câu trả lời mang tính chất đối phó, thiếu kiến thức kỹ thuật cơ bản và kỹ năng làm việc nhóm.',
  strengths_summary = 'Thao tác nộp bài nhanh, hoàn thành đủ các câu hỏi',
  weaknesses_summary = 'Hổng kiến thức nền tảng Backend (Spring Boot, JPA, CI/CD) và thái độ làm việc chưa phù hợp'
WHERE session_id = 2;
