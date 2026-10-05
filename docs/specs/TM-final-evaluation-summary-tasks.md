# Task Breakdown: Final & Milestone Mentorship Evaluation Hub

**Spec Reference:** [`TM-final-evaluation-summary-spec.md`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/docs/specs/TM-final-evaluation-summary-spec.md)  
**Branch:** `feature/mentor-evaluation-hub`  
**Status:** In Progress  

---

## Task List

- [x] **Task 1: Backend - Data Model & Repository**
  - Create entity `InternEvaluation.java` in `intern-and-program-service` (`org.example.internservice.intern.entity`).
  - Create repository `InternEvaluationRepository.java` with query by `internCode` and `evaluationType`.
  - Acceptance: Entity maps accurately to `intern_evaluations` table with all rating criteria and status.
  - Verify: Run `./gradlew.bat :intern-and-program-service:compileJava`. (SUCCESSFUL)
  - Files:
    - [`InternEvaluation.java`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternEvaluation.java)
    - [`InternEvaluationRepository.java`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/repository/InternEvaluationRepository.java)

- [x] **Task 2: Backend - DTOs, Service & Controller**
  - Create `InternEvaluationRequest.java` and `InternEvaluationResponse.java`.
  - Implement `InternEvaluationService` & `InternEvaluationServiceImpl` (calculating snapshot weekly score, draft/submit workflow).
  - Implement `InternEvaluationController` (`POST /api/interns/{internCode}/evaluations`, `GET /api/interns/{internCode}/evaluations`).
  - Acceptance: Endpoints return 200 OK with correct payload structure and error handling.
  - Verify: Run `./gradlew.bat :intern-and-program-service:compileJava`. (SUCCESSFUL)
  - Files:
    - [`InternEvaluationRequest.java`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/dto/InternEvaluationRequest.java)
    - [`InternEvaluationResponse.java`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/dto/InternEvaluationResponse.java)
    - [`InternEvaluationService.java`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/service/InternEvaluationService.java)
    - [`InternEvaluationServiceImpl.java`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/service/impl/InternEvaluationServiceImpl.java)
    - [`InternEvaluationController.java`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/controller/InternEvaluationController.java)

- [x] **Task 3: Frontend - Types & API Service**
  - Add TypeScript interfaces for `InternEvaluation`, `CreateEvaluationPayload`, and recommendation enums.
  - Add API methods in `assessmentService.ts` (`getEvaluation`, `saveEvaluation`).
  - Acceptance: Strict TypeScript types with 0 compile errors.
  - Verify: Run `cmd /c "npm.cmd run build"`. (SUCCESSFUL)
  - Files:
    - [`assessment.types.ts`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/types/assessment.types.ts)
    - [`assessmentService.ts`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/services/assessmentService.ts)

- [x] **Task 4: Frontend - Final Evaluation Component & Tab Integration**
  - Build `MentorFinalEvaluationTab.tsx` and styling in CSS module.
  - Include criteria for Technical skills, Work attitude, Soft skills, Strengths, Improvement areas, Recommendation dropdown.
  - Integrate as Tab 4 in `MentorInternDetailDrawer.tsx`.
  - Acceptance: Intuitive, responsive, clean light/dark mode UI with validation and toast notifications.
  - Verify: Run `cmd /c "npm.cmd run build"`. (SUCCESSFUL in 7.53s)
  - Files:
    - [`MentorFinalEvaluationTab.tsx`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/mentor/components/MentorFinalEvaluationTab.tsx)
    - [`MentorFinalEvaluationTab.module.css`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/mentor/components/MentorFinalEvaluationTab.module.css)
    - [`MentorInternDetailDrawer.tsx`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/mentor/components/MentorInternDetailDrawer.tsx)
