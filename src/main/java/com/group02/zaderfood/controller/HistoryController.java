package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.CalendarDayDTO;
import com.group02.zaderfood.dto.StatsDTO;
import com.group02.zaderfood.entity.UserProfile;
import com.group02.zaderfood.entity.UserWeightHistory;
import com.group02.zaderfood.repository.UserProfileRepository;
import com.group02.zaderfood.repository.UserWeightHistoryRepository;
import com.group02.zaderfood.service.CustomUserDetails;
import com.group02.zaderfood.service.MealPlanService;
import com.group02.zaderfood.service.UserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HistoryController {

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private UserProfileRepository userProfileRepository;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private UserWeightHistoryRepository weightRepo;

    @GetMapping("/meal-plan/history")
    public String showHistoryPage(Model model,
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {

        if (user == null) {
            return "redirect:/login";
        }

        // Mặc định là tháng hiện tại nếu không có param
        LocalDate now = LocalDate.now();
        if (month == null) {
            month = now.getMonthValue();
        }
        if (year == null) {
            year = now.getYear();
        }

        // 1. Lấy Goal từ Profile
        UserProfile profile = userProfileRepository.findById(user.getUserId()).orElse(new UserProfile());
        int userGoal = (profile.getCalorieGoalPerDay() != null) ? profile.getCalorieGoalPerDay() : 2000;

        // [FIX QUAN TRỌNG] Đẩy Profile vào Model để View sử dụng
        model.addAttribute("userProfile", profile);

        // 2. Lấy dữ liệu lịch
        List<CalendarDayDTO> calendarDays = mealPlanService.getMonthlyCalendar(user.getUserId(), month, year, userGoal);
        int startOffset = mealPlanService.getStartDayOffset(month, year);
        
        List<UserWeightHistory> weightHistory = weightRepo.findByUserIdOrderByRecordedAtAsc(user.getUserId());
        
        List<String> weightLabels = new ArrayList<>();
        List<Double> weightData = new ArrayList<>();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");

        for (UserWeightHistory w : weightHistory) {
            // Label trục X: Ngày tháng (VD: 12/12)
            weightLabels.add(w.getRecordedAt().format(fmt));
            // Data trục Y: Cân nặng
            weightData.add(w.getWeightKg().doubleValue());
        }

        model.addAttribute("weightLabels", weightLabels);
        model.addAttribute("weightData", weightData);

        // 3. Gửi dữ liệu sang View
        model.addAttribute("calendarDays", calendarDays);
        model.addAttribute("startOffset", startOffset);
        model.addAttribute("currentMonth", month);
        model.addAttribute("currentYear", year);
        model.addAttribute("monthName", java.time.Month.of(month).name());

        StatsDTO stats = mealPlanService.calculateStats(user.getUserId(), userGoal);
        model.addAttribute("stats", stats);

        return "mealplan/history-full";
    }
    
    @PostMapping("/user/weight/update")
    @ResponseBody
    public ResponseEntity<?> updateWeight(@RequestBody Map<String, String> payload, 
                                          @AuthenticationPrincipal CustomUserDetails user) {
        try {
            if (user == null) return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));

            String weightStr = payload.get("weight");
            BigDecimal newWeight = new BigDecimal(weightStr);

            // Gọi Service xử lý
            userService.updateCurrentWeight(user.getUserId(), newWeight);

            return ResponseEntity.ok(Map.of("status", "success", "message", "Weight updated & Plan adjusted!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
