package org.example.employeeservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    @GetMapping
    public String getEmployees() {
        return "Xin chào! API Gateway đã gọi sang Employee Service thành công!";
    }
}
