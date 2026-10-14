-- =========================================================================
-- BỔ SUNG CÁC QUYỀN MỚI CHO HỆ THỐNG INTERNHUB (C4, TM-28, TM-25, MEETING, TM-100)
-- =========================================================================

-- 1. MODULE ALLOWANCE (Quản lý phụ cấp & thu nhập)
INSERT IGNORE INTO permissions (code, name, module, description) VALUES
('ALLOWANCE_CALCULATE', 'Lập bảng tính phụ cấp', 'ALLOWANCE', 'Lập bảng tính xem trước phụ cấp theo ngày công và mức lương hợp đồng'),
('ALLOWANCE_ADJUST', 'Điều chỉnh số liệu phụ cấp', 'ALLOWANCE', 'Chỉnh sửa ngày công, thưởng phạt thủ công kèm lý do kiểm toán bắt buộc'),
('ALLOWANCE_APPROVE', 'Phê duyệt chi trả phụ cấp', 'ALLOWANCE', 'Phê duyệt bảng phụ cấp hàng loạt, mở khóa kỳ và duyệt bù truy lĩnh'),
('ALLOWANCE_EXPORT', 'Xuất Excel bảng phụ cấp', 'ALLOWANCE', 'Xuất danh sách phiếu chi phụ cấp chi tiết ra định dạng Excel'),
('ALLOWANCE_VIEW_OWN', 'Xem phiếu chi phụ cấp cá nhân', 'ALLOWANCE', 'Tra cứu phiếu chi phụ cấp và lịch sử thu nhập thực nhận cá nhân');

-- 2. MODULE SUPPORT (Yêu cầu hỗ trợ & giấy tờ)
INSERT IGNORE INTO permissions (code, name, module, description) VALUES
('SUPPORT_REQUEST_CREATE', 'Tạo yêu cầu hỗ trợ giấy tờ', 'SUPPORT', 'Nộp đơn xin cấp giấy xác nhận thực tập, chứng nhận hoàn thành, thư giới thiệu'),
('SUPPORT_REQUEST_REVIEW', 'Phê duyệt & phản hồi hỗ trợ', 'SUPPORT', 'Tiếp nhận, xử lý và tải lên file scan đóng dấu gửi lại cho thực tập sinh'),
('SUPPORT_REQUEST_VIEW_ALL', 'Xem tất cả ticket hỗ trợ', 'SUPPORT', 'Theo dõi và quản lý hàng đợi ticket hỗ trợ toàn công ty kèm cảnh báo SLA');

-- 3. MODULE LEAVE (Quản lý nghỉ phép)
INSERT IGNORE INTO permissions (code, name, module, description) VALUES
('LEAVE_REQUEST_CREATE', 'Tạo đơn xin nghỉ phép', 'LEAVE', 'Soạn thảo và nộp đơn xin nghỉ phép nửa ngày hoặc cả ngày'),
('LEAVE_REQUEST_APPROVE', 'Phê duyệt đơn nghỉ phép', 'LEAVE', 'Duyệt hoặc từ chối đơn xin nghỉ phép của thực tập sinh phụ trách'),
('LEAVE_REQUEST_VIEW_ALL', 'Xem lịch nghỉ phép toàn công ty', 'LEAVE', 'Xem báo cáo và lịch trình nghỉ phép của toàn bộ thực tập sinh');

-- 4. MODULE MEETING (Lịch họp & trao đổi định kỳ)
INSERT IGNORE INTO permissions (code, name, module, description) VALUES
('MEETING_SCHEDULE_MANAGE', 'Quản lý lịch họp', 'MEETING', 'Tạo, dời lịch hoặc hủy cuộc họp trực tuyến (Google Meet/Jitsi) và trực tiếp'),
('MEETING_SCHEDULE_VIEW_ALL', 'Xem lịch họp toàn công ty', 'MEETING', 'Xem danh sách các cuộc họp của toàn bộ phòng ban và chương trình'),
('MEETING_SCHEDULE_VIEW_OWN', 'Xem lịch họp cá nhân', 'MEETING', 'Nhận thông báo và tham gia các cuộc họp được mời');

-- 5. MỞ RỘNG PROGRAM & CONTRACT
INSERT IGNORE INTO permissions (code, name, module, description) VALUES
('PROGRAM_IMPORT_EXCEL', 'Nhập danh sách TTS từ Excel', 'PROGRAM', 'Nhập danh sách thực tập sinh hàng loạt vào chương trình từ file Excel'),
('CONTRACT_TEMPLATE_MANAGE', 'Quản lý mẫu hợp đồng động', 'CONTRACT', 'Soạn thảo, cấu hình token và ban hành mẫu hợp đồng điện tử v2.0');

-- =========================================================================
-- GÁN QUYỀN MẶC ĐỊNH CHO CÁC VAI TRÒ HỆ THỐNG
-- Role IDs chuẩn: ADMIN = 1, HR = 2, MENTOR = 3, INTERN = 4
-- =========================================================================

-- A. ADMIN (Nhận toàn bộ quyền mới)
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT 1, p.id FROM permissions p
WHERE p.code IN (
    'ALLOWANCE_CALCULATE', 'ALLOWANCE_ADJUST', 'ALLOWANCE_APPROVE', 'ALLOWANCE_EXPORT', 'ALLOWANCE_VIEW_OWN',
    'SUPPORT_REQUEST_CREATE', 'SUPPORT_REQUEST_REVIEW', 'SUPPORT_REQUEST_VIEW_ALL',
    'LEAVE_REQUEST_CREATE', 'LEAVE_REQUEST_APPROVE', 'LEAVE_REQUEST_VIEW_ALL',
    'MEETING_SCHEDULE_MANAGE', 'MEETING_SCHEDULE_VIEW_ALL', 'MEETING_SCHEDULE_VIEW_OWN',
    'PROGRAM_IMPORT_EXCEL', 'CONTRACT_TEMPLATE_MANAGE'
);

-- B. HR (Quyền quản trị nghiệp vụ C4, Nghỉ phép, Họp, Mẫu hợp đồng)
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT 2, p.id FROM permissions p
WHERE p.code IN (
    'ALLOWANCE_CALCULATE', 'ALLOWANCE_ADJUST', 'ALLOWANCE_APPROVE', 'ALLOWANCE_EXPORT',
    'SUPPORT_REQUEST_REVIEW', 'SUPPORT_REQUEST_VIEW_ALL',
    'LEAVE_REQUEST_APPROVE', 'LEAVE_REQUEST_VIEW_ALL',
    'MEETING_SCHEDULE_MANAGE', 'MEETING_SCHEDULE_VIEW_ALL', 'MEETING_SCHEDULE_VIEW_OWN',
    'PROGRAM_IMPORT_EXCEL', 'CONTRACT_TEMPLATE_MANAGE'
);

-- C. MENTOR (Duyệt nghỉ phép, Quản lý lịch họp phụ trách)
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT 3, p.id FROM permissions p
WHERE p.code IN (
    'LEAVE_REQUEST_APPROVE', 'LEAVE_REQUEST_VIEW_ALL',
    'MEETING_SCHEDULE_MANAGE', 'MEETING_SCHEDULE_VIEW_OWN'
);

-- D. INTERN (Các quyền nộp đơn, xem phiếu lương, xem lịch họp cá nhân)
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT 4, p.id FROM permissions p
WHERE p.code IN (
    'ALLOWANCE_VIEW_OWN',
    'SUPPORT_REQUEST_CREATE',
    'LEAVE_REQUEST_CREATE',
    'MEETING_SCHEDULE_VIEW_OWN'
);
