package com.group02.zaderfood.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ai-tools")
public class AiToolsController {

    @GetMapping("/calorie-estimator")
    public String showCalorieEstimatorPage() {
        // Trả về tên file template (không cần đuôi .html)
        // File này sẽ nằm trong thư mục src/main/resources/templates/
        return "ai/ai-calorie-estimator";
    }
}