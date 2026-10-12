package org.example.internservice.attendance.report.util;

import org.example.internservice.attendance.report.dto.response.InternDiligenceReportResponse;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Tiện ích kết xuất báo cáo chuyên cần sang định dạng CSV có UTF-8 BOM hiển thị chuẩn trên Excel (TM-26).
 */
@Component
public class AttendanceCsvExporter {

    private static final String CSV_HEADER = "Mã Thực Tập Sinh,Họ và Tên,Email,Phòng Ban,Chương Trình,Mentor Phụ Trách," +
            "Ngày Công Tiêu Chuẩn,Ngày Đi Làm Thực Tế,Đúng Giờ,Đi Muộn,Về Sớm,Tổng Giờ Làm,Nghỉ Có Phép,Vắng Không Phép," +
            "Tỷ Lệ Chuyên Cần (%),Xếp Loại,Ghi Chú Cảnh Báo";

    public byte[] exportToCsv(List<InternDiligenceReportResponse> reports) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Ghi UTF-8 Byte Order Mark (BOM) để Microsoft Excel trên Windows nhận diện đúng tiếng Việt có dấu
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            writer.write('\uFEFF');
            writer.println(CSV_HEADER);

            if (reports != null) {
                for (InternDiligenceReportResponse item : reports) {
                    writer.println(String.join(",",
                            escapeCsv(item.getInternCode()),
                            escapeCsv(item.getFullName()),
                            escapeCsv(item.getEmail()),
                            escapeCsv(item.getDepartmentName()),
                            escapeCsv(item.getProgramTitle()),
                            escapeCsv(item.getMentorName()),
                            String.valueOf(item.getExpectedWorkDays() != null ? item.getExpectedWorkDays() : 0),
                            String.valueOf(item.getActualWorkDays() != null ? item.getActualWorkDays() : 0.0),
                            String.valueOf(item.getOnTimeCount() != null ? item.getOnTimeCount() : 0),
                            String.valueOf(item.getLateCount() != null ? item.getLateCount() : 0),
                            String.valueOf(item.getEarlyLeaveCount() != null ? item.getEarlyLeaveCount() : 0),
                            String.valueOf(item.getTotalWorkingHours() != null ? item.getTotalWorkingHours() : 0.0),
                            String.valueOf(item.getApprovedLeaveDays() != null ? item.getApprovedLeaveDays() : 0.0),
                            String.valueOf(item.getUnexcusedAbsenceDays() != null ? item.getUnexcusedAbsenceDays() : 0.0),
                            String.format("%.2f%%", item.getDiligenceRate() != null ? item.getDiligenceRate() : 0.0),
                            escapeCsv(item.getDiligenceStatusTitle()),
                            escapeCsv(item.getWarningNotes())
                    ));
                }
            }
            writer.flush();
        }

        return out.toByteArray();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "\"\"";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
