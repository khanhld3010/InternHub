INSERT INTO interview_evaluations (session_id, version, is_active, total_score, hr_final_total_score, ai_recommendation, proctoring_status, ai_summary, strengths_summary, weaknesses_summary, hr_status, grading_status, created_at, updated_at) 
VALUES (2, 1, 1, 15.00, 15.00, 'REJECTED', 'CLEAN', 'Ứng viên trả lời mang tính chất đối phó, chưa đưa ra giải pháp kỹ thuật cụ thể.', 'Tự tin, phản hồi nhanh', 'Chưa nắm vững kiến thức lập trình cơ bản và xử lý ngoại lệ', 'PENDING_REVIEW', 'COMPLETED', NOW(), NOW());

INSERT INTO interview_answer_evaluations (evaluation_id, question_id, score, hr_override_score, weight, technical_accuracy_score, optimization_score, articulation_score, edge_cases_score, evidence_quote, rationale, suggested_solution, is_flagged_suspicious)
VALUES 
(LAST_INSERT_ID(), 16, 10.00, NULL, 1.00, 10.00, 10.00, 10.00, 10.00, 'chịu chết', 'Ứng viên không trả lời được câu hỏi thuật toán cơ bản.', 'Cần sử dụng cấu trúc dữ liệu HashMap để tối ưu tìm kiếm.', 0),
(LAST_INSERT_ID(), 17, 20.00, NULL, 1.00, 20.00, 20.00, 20.00, 20.00, 'Quá khó', 'Ứng viên chưa nắm được độ phức tạp thuật toán.', 'Nên giải thích về Time Complexity O(N log N) của Merge Sort.', 0);
