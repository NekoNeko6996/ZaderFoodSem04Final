package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.CalendarDayDTO;
import com.group02.zaderfood.dto.StatsDTO;
import com.group02.zaderfood.entity.UserProfile;
import com.group02.zaderfood.repository.UserProfileRepository;
import com.group02.zaderfood.service.CustomUserDetails;
import com.group02.zaderfood.service.MealPlanService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HistoryController {

    @Autowired
    private MealPlanService mealPlanService;
    
    @Autowired 
    private UserProfileRepository userProfileRepository;

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

        // 1. Lấy Goal từ Profile để so sánh
        UserProfile profile = userProfileRepository.findById(user.getUserId()).orElse(null);
        int userGoal = (profile != null && profile.getCalorieGoalPerDay() != null) ? profile.getCalorieGoalPerDay() : 2000;

        // 2. Lấy dữ liệu lịch
        List<CalendarDayDTO> calendarDays = mealPlanService.getMonthlyCalendar(user.getUserId(), month, year, userGoal);
        int startOffset = mealPlanService.getStartDayOffset(month, year);

        // 3. Gửi dữ liệu sang View
        model.addAttribute("calendarDays", calendarDays);
        model.addAttribute("startOffset", startOffset); // Số ô trống đầu tháng
        model.addAttribute("currentMonth", month);
        model.addAttribute("currentYear", year);
        model.addAttribute("monthName", java.time.Month.of(month).name()); // Tên tháng (DECEMBER)

        StatsDTO stats = mealPlanService.calculateStats(user.getUserId(), userGoal);
        model.addAttribute("stats", stats);
        
        return "mealplan/history-full";
    }
}
