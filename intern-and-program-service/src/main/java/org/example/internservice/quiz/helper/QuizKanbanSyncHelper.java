package org.example.internservice.quiz.helper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.MentorProfile;
import org.example.internservice.intern.repository.MentorProfileRepository;
import org.example.internservice.mission.entity.MissionBoard;
import org.example.internservice.mission.entity.MissionItem;
import org.example.internservice.mission.entity.enums.BoardStatus;
import org.example.internservice.mission.entity.enums.MissionItemStatus;
import org.example.internservice.mission.entity.enums.MissionPriority;
import org.example.internservice.mission.repository.MissionBoardRepository;
import org.example.internservice.mission.repository.MissionItemRepository;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.quiz.entity.Quiz;
import org.example.internservice.quiz.entity.QuizAssignment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuizKanbanSyncHelper {

    private final MissionBoardRepository missionBoardRepository;
    private final MissionItemRepository missionItemRepository;
    private final MentorProfileRepository mentorProfileRepository;

    @Transactional
    public MissionItem createKanbanTaskForQuizAssignment(QuizAssignment assignment, Quiz quiz) {
        if (assignment.getIntern() == null) {
            log.warn("Không thể tạo thẻ Kanban cho Assignment ID: {} do không có Intern gắn kèm", assignment.getId());
            return null;
        }

        InternshipProgram program = quiz.getProgram();
        if (program == null) {
            return null;
        }

        try {
            // Tìm Board của chương trình, nếu chưa có thì tạo Board mặc định
            List<MissionBoard> boards = missionBoardRepository.findByProgramIdWithDetails(program.getId());
            MissionBoard targetBoard;
            if (!boards.isEmpty()) {
                targetBoard = boards.get(0);
            } else {
                MentorProfile mentor = quiz.getMentorId() != null
                        ? mentorProfileRepository.findById(quiz.getMentorId()).orElse(null)
                        : null;
                if (mentor == null) {
                    log.warn("Không thể tạo MissionBoard vì không tìm thấy MentorProfile tương ứng với ID: {}", quiz.getMentorId());
                    return null;
                }

                targetBoard = MissionBoard.builder()
                        .program(program)
                        .mentor(mentor)
                        .title("Bảng Nhiệm Vụ - " + program.getName())
                        .description("Bảng nhiệm vụ tự động khởi tạo cho kỳ thực tập")
                        .status(BoardStatus.ACTIVE)
                        .build();
                targetBoard = missionBoardRepository.save(targetBoard);
                log.info("Đã tự động khởi tạo MissionBoard ID: {} cho Chương trình ID: {}", targetBoard.getId(), program.getId());
            }

            // Tạo thẻ nhiệm vụ [Quiz]
            InternProfile intern = assignment.getIntern();
            MissionItem item = MissionItem.builder()
                    .board(targetBoard)
                    .title("[Quiz] " + quiz.getTitle())
                    .description("Bài kiểm tra trắc nghiệm ôn tập. Thời lượng: " + quiz.getTimeLimitMinutes() +
                            " phút. Điểm đạt: " + quiz.getPassingScore() + " điểm.")
                    .priority(MissionPriority.MEDIUM)
                    .status(MissionItemStatus.TODO)
                    .dueDate(assignment.getDueDate())
                    .submissionUrl("/intern/quizzes/" + assignment.getId() + "/take")
                    .assignees(new HashSet<>(Collections.singletonList(intern)))
                    .orderIndex(0)
                    .build();

            MissionItem savedItem = missionItemRepository.save(item);
            assignment.setMissionItem(savedItem);
            log.info("Đã tự động tạo thẻ Kanban MissionItem ID: {} cho TTS {} (Assignment ID: {})",
                    savedItem.getId(), intern.getInternCode(), assignment.getId());
            return savedItem;
        } catch (Exception e) {
            log.error("Lỗi khi tự động đồng bộ thẻ Kanban cho Quiz Assignment ID: {}: {}",
                    assignment.getId(), e.getMessage(), e);
            return null;
        }
    }

    @Transactional
    public void markKanbanTaskCompleted(MissionItem missionItem, Integer score, Integer totalPoints, BigDecimal percentageScore) {
        markKanbanTaskCompleted(missionItem, score, totalPoints, percentageScore, true);
    }

    @Transactional
    public void markKanbanTaskCompleted(MissionItem missionItem, Integer score, Integer totalPoints, BigDecimal percentageScore, boolean passed) {
        if (missionItem == null) {
            return;
        }

        try {
            missionItem.setStatus(MissionItemStatus.COMPLETED);
            missionItem.setSubmittedAt(LocalDateTime.now());
            String resultText = passed ? "Đạt kết quả yêu cầu." : "Hoàn thành bài thi.";
            missionItem.setCompletionNote("Đã hoàn thành bài Quiz. Điểm số: " + score + "/" + totalPoints +
                    " (" + percentageScore + "%). " + resultText);
            missionItemRepository.save(missionItem);
            log.info("Đã tự động chuyển thẻ Kanban ID: {} sang COMPLETED", missionItem.getId());
        } catch (Exception e) {
            log.error("Lỗi khi cập nhật trạng thái thẻ Kanban ID: {}: {}", missionItem.getId(), e.getMessage(), e);
        }
    }
}
