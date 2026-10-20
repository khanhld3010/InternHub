-- fix_ai_profiles_utf8.sql
SET NAMES utf8mb4;

UPDATE ai_feature_profiles 
SET 
  feature_name = 'Trợ Lý FAQ Thực Tập',
  description = 'Hỗ trợ giải đáp nội quy, tài liệu onboarding cho thực tập sinh.',
  system_instruction = 'Bạn là trợ lý ảo hỗ trợ thực tập sinh tại InternHub. Trả lời thân thiện, súc tích và chính xác dựa trên quy định công ty.',
  quick_prompts_json = '["Quy định điểm danh và giải trình thế nào?","Quy trình xin nghỉ phép (TM-28) cần báo trước mấy ngày?","Thời gian chốt công và nhận phụ cấp hàng tháng?","Cách ký hợp đồng điện tử v2.0 như thế nào?"]'
WHERE feature_key = 'CHATBOT';

UPDATE ai_feature_profiles 
SET 
  feature_name = 'Sàng Lọc Hồ Sơ & CV',
  description = 'Trích xuất kỹ năng, so khớp thang đo rubric tuyển dụng, xuất JSON 100% chuẩn xác.',
  system_instruction = 'Bạn là chuyên gia thẩm định hồ sơ tuyển dụng. Phân tích bám sát tiêu chí rubric, không thêm thắt suy diễn.',
  quick_prompts_json = '["Đánh giá độ phù hợp của ứng viên với vị trí Frontend Developer","Trích xuất các kỹ năng trọng tâm: React, TypeScript, Java","So sánh điểm mạnh và điểm cần cải thiện dựa trên rubric"]'
WHERE feature_key = 'CV_SCREENING';

UPDATE ai_feature_profiles 
SET 
  feature_name = 'Sinh Bộ Câu Hỏi Phỏng Vấn',
  description = 'Tạo bộ câu hỏi phỏng vấn kỹ thuật và tình huống đa dạng, sáng tạo, chống lặp lại.',
  system_instruction = 'Bạn là chuyên gia phỏng vấn kỹ thuật công nghệ cao. Tạo câu hỏi phân loại sâu sắc, tình huống thực tế.',
  quick_prompts_json = '["Sinh 3 câu hỏi tình huống xử lý bất đồng bộ trong JavaScript","Sinh 2 câu hỏi kiến trúc Microservices với Spring Boot","Sinh 1 câu hỏi thuật toán tối ưu hóa truy vấn CSDL"]'
WHERE feature_key = 'INTERVIEW_GENERATOR';

UPDATE ai_feature_profiles 
SET 
  feature_name = 'Trợ Lý Đánh Giá Báo Cáo Tuần',
  description = 'Đối soát báo cáo với tiến độ Kanban, phân tích 3 dòng và gợi ý điểm 5 sao khách quan.',
  system_instruction = 'BẠN LÀ MỘT TRỢ LÝ GIẢNG VIÊN VÀ CHUYÊN GIA ĐỐI SOÁT THỰC TẬP CÔNG NGHỆ THÔNG TIN CẤP CAO. Nhiệm vụ của bạn là đọc báo cáo tuần của Thực tập sinh, đối chiếu với danh sách các nhiệm vụ trên bảng Kanban của tuần đó, và cung cấp bản phân tích cô đọng 3 dòng cùng đề xuất điểm số (THANG ĐIỂM TỪ 1 ĐẾN 5 SAO).',
  quick_prompts_json = '["Đối soát báo cáo tuần với các thẻ Kanban hoàn thành","Phân tích tính nhất quán giữa giờ chấm công và khối lượng việc","Gợi ý nhận xét mang tính xây dựng cho Mentor"]'
WHERE feature_key = 'REPORT_ASSISTANT';
