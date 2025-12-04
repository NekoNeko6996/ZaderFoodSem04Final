package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.WeeklyPlanDTO;
import com.group02.zaderfood.entity.UserDietaryPreference;
import com.group02.zaderfood.entity.UserProfile;
import com.group02.zaderfood.repository.UserDietaryPreferenceRepository; // Bạn cần tạo repo này
import com.group02.zaderfood.repository.UserProfileRepository;
import com.group02.zaderfood.service.AiFoodService;
import com.group02.zaderfood.service.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/meal-plan")
public class MealPlanController {

    @Autowired
    private AiFoodService aiFoodService;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserDietaryPreferenceRepository dietaryRepo;

    // 1. Hiển thị trang tạo (Generate Page) - Đã update logic lấy dữ liệu
    @GetMapping("/generate")
    public String showGeneratePage(Model model,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        // Default values
        int defaultCalories = 2000;
        String defaultDiet = "Balanced";
        boolean missingData = false;

        if (currentUser != null) {
            // A. Lấy thông tin Profile (Calories Goal)
            UserProfile profile = userProfileRepository.findById(currentUser.getUserId()).orElse(null);

            if (profile != null && profile.getCalorieGoalPerDay() != null && profile.getCalorieGoalPerDay() > 0) {
                defaultCalories = profile.getCalorieGoalPerDay();
            } else {
                // Nếu chưa có goal -> Đánh dấu thiếu dữ liệu
                missingData = true;
            }

            // B. Lấy thông tin Diet Preference
            List<UserDietaryPreference> diets = dietaryRepo.findByUserId(currentUser.getUserId());
            if (!diets.isEmpty()) {
                // Lấy kiểu ăn đầu tiên tìm thấy (hoặc logic ưu tiên của bạn)
                defaultDiet = diets.get(0).getDietType().name(); // Giả sử Enum tên khớp value
            }
        }

        // Đẩy dữ liệu ra View
        model.addAttribute("currentCalories", defaultCalories);
        model.addAttribute("currentDiet", defaultDiet);
        model.addAttribute("missingData", missingData); // Cờ để bật SweetAlert

        return "mealplan/generate";
    }

    // 2. Xử lý tạo plan (Giữ nguyên logic cũ, chỉ map lại UI)
    @PostMapping("/generate")
    public String generatePlan(
            @RequestParam int calories,
            @RequestParam String dietType,
            @RequestParam String goal,
            RedirectAttributes redirectAttributes,
            HttpSession session) {

        try {
            WeeklyPlanDTO plan = aiFoodService.generateWeeklyPlan(calories, dietType, goal);

            if (plan != null && plan.days != null && !plan.days.isEmpty()) {
                session.setAttribute("currentWeeklyPlan", plan);
                redirectAttributes.addFlashAttribute("success", "Weekly plan generated successfully!");
                return "redirect:/meal-plan/customize";
            } else {
                redirectAttributes.addFlashAttribute("error", "AI could not generate a valid plan. Try again.");
                return "redirect:/meal-plan/generate";
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "System Error: " + e.getMessage());
            return "redirect:/meal-plan/generate";
        }
    }

    // 3. Customize Page (Giữ nguyên)
    @GetMapping("/customize")
    public String showCustomizePage(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        WeeklyPlanDTO plan = (WeeklyPlanDTO) session.getAttribute("currentWeeklyPlan");
        if (plan == null) {
            redirectAttributes.addFlashAttribute("error", "Please generate a plan first.");
            return "redirect:/meal-plan/generate";
        }
        model.addAttribute("weeklyPlan", plan);
        return "mealplan/customize";
    }
}
