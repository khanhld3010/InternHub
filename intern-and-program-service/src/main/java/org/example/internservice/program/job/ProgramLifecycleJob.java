package org.example.internservice.program.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.entity.enums.ProgramStatus;
import org.example.internservice.program.repository.InternshipProgramRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramLifecycleJob {

    private final InternshipProgramRepository programRepository;
    private final InternProfileRepository internProfileRepository;

    private static final List<InternStatus> ACTIVE_STATUSES = List.of(
            InternStatus.APPROVED, InternStatus.INTERNING, InternStatus.COMPLETED
    );

    @Scheduled(cron = "0 0 1 * * ?") // Runs daily at 01:00 AM
    @Transactional
    public void executeProgramLifecycleTransitions() {
        LocalDate today = LocalDate.now();
        log.info("Bắt đầu chạy Cron Job quét vòng đời chương trình thực tập cho ngày {}", today);

        // 1. OPEN -> ONGOING
        List<InternshipProgram> openPrograms = programRepository.findOpenProgramsReadyToStart(today);
        for (InternshipProgram program : openPrograms) {
            program.setStatus(ProgramStatus.ONGOING);
            programRepository.save(program);
            log.info("Chương trình {} ({}) tự động chuyển OPEN -> ONGOING vào ngày {}", program.getName(), program.getProgramCode(), today);
        }

        // 2. PLANNING -> OPEN -> ONGOING (nếu HR quên mở tuyển nhưng đã tới ngày bắt đầu và có TTS)
        List<InternshipProgram> planningPrograms = programRepository.findPlanningProgramsReachedStartDate(today);
        for (InternshipProgram program : planningPrograms) {
            boolean hasActiveInterns = internProfileRepository.existsByProgramIdAndStatusIn(program.getId(), ACTIVE_STATUSES);
            if (hasActiveInterns) {
                program.setStatus(ProgramStatus.ONGOING);
                programRepository.save(program);
                log.info("Chương trình {} ({}) tự động chuỗi PLANNING -> OPEN -> ONGOING vì đã có TTS tiếp nhận", program.getName(), program.getProgramCode());
            }
        }

        // 3. Grace Period 7 Days: Hủy chương trình PLANNING bỏ hoang quá 7 ngày kể từ startDate mà không có TTS
        LocalDate thresholdGraceDate = today.minusDays(7);
        List<InternshipProgram> pastPlanningPrograms = programRepository.findPlanningProgramsPastStartDate(thresholdGraceDate);
        for (InternshipProgram program : pastPlanningPrograms) {
            boolean hasActiveInterns = internProfileRepository.existsByProgramIdAndStatusIn(program.getId(), ACTIVE_STATUSES);
            if (!hasActiveInterns) {
                program.setStatus(ProgramStatus.CANCELLED);
                program.setIsRecruitmentOpen(false);
                program.setCancellationReason("Hệ thống tự động hủy chương trình do quá 7 ngày kể từ ngày bắt đầu dự kiến mà không có thực tập sinh tiếp nhận");
                programRepository.save(program);
                log.warn("Chương trình {} ({}) bị tự động hủy sau 7 ngày ân hạn không có TTS", program.getName(), program.getProgramCode());
            }
        }

        // 4. ONGOING -> COMPLETED (sau khi ngày làm việc cuối cùng endDate kết thúc: today > endDate)
        List<InternshipProgram> ongoingPrograms = programRepository.findOngoingProgramsPastEndDate(today);
        for (InternshipProgram program : ongoingPrograms) {
            program.setStatus(ProgramStatus.COMPLETED);
            program.setIsRecruitmentOpen(false);
            programRepository.save(program);
            log.info("Chương trình {} ({}) tự động chuyển ONGOING -> COMPLETED sau ngày làm việc cuối cùng {}", program.getName(), program.getProgramCode(), program.getEndDate());
        }

        log.info("Hoàn thành chu kỳ quét vòng đời chương trình thực tập.");
    }
}
