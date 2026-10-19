UPDATE interview_evaluations 
SET 
  model_name = 'gemini-3.5-flash-lite',
  ai_summary = 'Ứng viên trả lời mang tính chất đối phó, chưa đưa ra giải pháp kỹ thuật cụ thể.',
  strengths_summary = 'Tự tin, phản hồi nhanh',
  weaknesses_summary = 'Chưa nắm vững kiến thức lập trình cơ bản và xử lý ngoại lệ'
WHERE session_id = 2;

UPDATE interview_answer_evaluations 
SET 
  rationale = 'Ứng viên không trả lời được câu hỏi thuật toán cơ bản.',
  evidence_quote = 'chịu chết',
  suggested_solution = 'Cần sử dụng cấu trúc dữ liệu HashMap để tối ưu tìm kiếm.'
WHERE evaluation_id = 2 AND question_id = 16;

UPDATE interview_answer_evaluations 
SET 
  rationale = 'Ứng viên chưa nắm được độ phức tạp thuật toán.',
  evidence_quote = 'Quá khó',
  suggested_solution = 'Nên giải thích về Time Complexity O(N log N) của Merge Sort.'
WHERE evaluation_id = 2 AND question_id = 17;
