package org.example.internservice.program.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.program.entity.Department;
import org.example.internservice.program.repository.DepartmentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DepartmentDataInitializer implements CommandLineRunner {

    private final DepartmentRepository departmentRepository;

    @Override
    public void run(String... args) {
        if (departmentRepository.count() == 0) {
            log.info("Khoi tao du lieu phong ban mac dinh cho Department...");
            List<Department> initialDepartments = List.of(
                    Department.builder()
                            .name("Trung tâm Phát triển Phần mềm")
                            .code("IT-DEV")
                            .description("Phụ trách nghiên cứu & phát triển các hệ thống phần mềm")
                            .status("ACTIVE")
                            .build(),
                    Department.builder()
                            .name("Bộ phận Đảm bảo Chất lượng")
                            .code("QA")
                            .description("Phụ trách kiểm thử chất lượng sản phẩm & quy trình")
                            .status("ACTIVE")
                            .build(),
                    Department.builder()
                            .name("Bộ phận An toàn & Bảo mật")
                            .code("SEC")
                            .description("Phụ trách an toàn thông tin & bảo mật hệ thống")
                            .status("ACTIVE")
                            .build(),
                    Department.builder()
                            .name("Phòng Nhân sự & Đào tạo")
                            .code("HR-TD")
                            .description("Quản trị nhân sự, đào tạo và phát triển nhân tài thực tập")
                            .status("ACTIVE")
                            .build()
            );

            departmentRepository.saveAll(initialDepartments);
            log.info("Đã khởi tạo thành công {} phòng ban mặc định!", initialDepartments.size());
        }
    }
}
