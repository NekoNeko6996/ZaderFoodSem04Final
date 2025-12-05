package com.group02.zaderfood.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group02.zaderfood.dto.SavePlanDTO;
import com.group02.zaderfood.dto.WeeklyPlanDTO;
import com.group02.zaderfood.entity.DailyMealPlan;
import com.group02.zaderfood.entity.MealItem;
import com.group02.zaderfood.entity.RecipeCollection;
import com.group02.zaderfood.entity.UserDietaryPreference;
import com.group02.zaderfood.entity.UserProfile;
import com.group02.zaderfood.repository.DailyMealPlanRepository;
import com.group02.zaderfood.repository.MealItemRepository;
import com.group02.zaderfood.repository.RecipeCollectionRepository;
import com.group02.zaderfood.repository.UserDietaryPreferenceRepository; // Bạn cần tạo repo này
import com.group02.zaderfood.repository.UserProfileRepository;
import com.group02.zaderfood.service.AiFoodService;
import com.group02.zaderfood.service.CustomUserDetails;
import com.group02.zaderfood.service.MealPlanService;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/meal-plan")
public class MealPlanController {

    @Autowired
    private AiFoodService aiFoodService;

    @Autowired
    private RecipeCollectionRepository collectionRepo;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserDietaryPreferenceRepository dietaryRepo;

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private DailyMealPlanRepository dailyRepo;

    @Autowired
    private MealItemRepository itemRepo;

    private String getDayLabel(LocalDate date) {
        // EEEE: Tên thứ (Monday), dd/MM: Ngày tháng
        return date.format(DateTimeFormatter.ofPattern("EEEE dd/MM"));
    }

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

        if (currentUser != null) {
            // Giả sử bạn có hàm này trong MealPlanService
            List<DailyMealPlan> history = mealPlanService.getRecentPlans(currentUser.getUserId());
            model.addAttribute("planHistory", history);
        }

        return "mealplan/generate";
    }

    // 2. Xử lý tạo plan (Giữ nguyên logic cũ, chỉ map lại UI)
    @PostMapping("/generate")
    @ResponseBody // Bắt buộc: Để trả về JSON thay vì tìm file HTML
    public ResponseEntity<?> generatePlan(
            @RequestParam int calories,
            @RequestParam String dietType,
            @RequestParam String goal,
            @RequestParam(required = false) String startDateStr,
            HttpSession session) { // Bỏ RedirectAttributes vì dùng JSON

        try {
            LocalDate startDate = (startDateStr != null && !startDateStr.isEmpty())
                    ? LocalDate.parse(startDateStr)
                    : LocalDate.now().plusDays(1);

            System.out.println("--- AI REQUEST: " + calories + "kcal | " + dietType + " ---");

            // Gọi AI
            WeeklyPlanDTO plan = aiFoodService.generateWeeklyPlan(calories, dietType, goal);

            if (plan != null && plan.days != null && !plan.days.isEmpty()) {
                checkConflictsAndAssignDates(plan, startDate, "NEW_AI", (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
                for (int i = 0; i < plan.days.size(); i++) {
                    plan.days.get(i).dayName = getDayLabel(startDate.plusDays(i));
                }

                session.setAttribute("currentWeeklyPlan", plan);
                session.setAttribute("manualTargetCalories", calories);
                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "redirectUrl", "/meal-plan/customize",
                        "message", "Weekly plan created successfully!"
                ));
            } else {
                // THẤT BẠI: Trả về lỗi để JS hiện Toast
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "AI response is empty. Please check Ollama connection."
                ));
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "Server Error: " + e.getMessage()
            ));
        }
    }

    // 3. Customize Page (Giữ nguyên)
    @GetMapping("/customize")
    public String showCustomizePage(Model model, HttpSession session, RedirectAttributes redirectAttributes, @AuthenticationPrincipal CustomUserDetails user) {
        WeeklyPlanDTO plan = (WeeklyPlanDTO) session.getAttribute("currentWeeklyPlan");
        if (plan == null) {
            redirectAttributes.addFlashAttribute("error", "Please generate a plan first.");
            return "redirect:/meal-plan/generate";
        }
        model.addAttribute("weeklyPlan", plan);

        if (user != null) {
            List<RecipeCollection> myCollections = collectionRepo.findByUserId(user.getUserId());
            model.addAttribute("myCollections", myCollections);
        }

        // --- SỬA LỖI TẠI ĐÂY ---
        int targetCalories = 2000;
        Integer sessionTarget = (Integer) session.getAttribute("manualTargetCalories");
        if (sessionTarget != null) {
            targetCalories = sessionTarget;
        } else if (user != null) {
            // 2. Nếu không có trong session, mới lấy từ DB Profile
            UserProfile profile = userProfileRepository.findById(user.getUserId()).orElse(null);
            if (profile != null && profile.getCalorieGoalPerDay() != null) {
                targetCalories = profile.getCalorieGoalPerDay();
            }
        }
        // [QUAN TRỌNG] Dòng này bị thiếu trong code cũ của bạn
        model.addAttribute("targetCalories", targetCalories);
        // -----------------------

        return "mealplan/customize";
    }

    @GetMapping("/manual")
    @PostMapping("/manual")
    public String manualStart(
            @RequestParam(required = false) String startDateStr,
            @RequestParam(required = false, defaultValue = "2000") int calories, // [MỚI] Nhận Calories
            HttpSession session,
            @AuthenticationPrincipal CustomUserDetails user) {

        LocalDate startDate = (startDateStr != null && !startDateStr.isEmpty())
                ? LocalDate.parse(startDateStr)
                : LocalDate.now().plusDays(1);

        WeeklyPlanDTO emptyPlan = new WeeklyPlanDTO();
        emptyPlan.days = new ArrayList<>();

        System.err.println(startDateStr);

        // Tạo 7 ngày rỗng
        for (int i = 0; i < 7; i++) {
            WeeklyPlanDTO.DailyPlan day = new WeeklyPlanDTO.DailyPlan();
            day.meals = new ArrayList<>();
            day.meals.add(createEmptyMeal("Breakfast"));
            day.meals.add(createEmptyMeal("Lunch"));
            day.meals.add(createEmptyMeal("Dinner"));
            day.totalCalories = 0;
            emptyPlan.days.add(day);
        }

        // Kiểm tra xung đột
        checkConflictsAndAssignDates(emptyPlan, startDate, "NEW_MANUAL", user);
        session.setAttribute("manualTargetCalories", calories);

        session.setAttribute("currentWeeklyPlan", emptyPlan);
        return "redirect:/meal-plan/customize";
    }

    // Add ObjectMapper as a field in your controller or create a new instance inside the method if needed
    // private final ObjectMapper objectMapper = new ObjectMapper(); 
    private void checkConflictsAndAssignDates(WeeklyPlanDTO plan, LocalDate startDate, String sourceMode, CustomUserDetails user) {
        if (user == null) {
            return;
        }
        Integer userId = user.getUserId();

        // Initialize ObjectMapper for JSON conversion
        ObjectMapper mapper = new ObjectMapper();

        for (int i = 0; i < plan.days.size(); i++) {
            WeeklyPlanDTO.DailyPlan dayDto = plan.days.get(i);
            LocalDate currentDate = startDate.plusDays(i);

            // 1. Assign date info
            dayDto.dateString = currentDate.toString();
            dayDto.dayName = currentDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE dd/MM"));

            // 2. Check DB for conflicts
            Optional<DailyMealPlan> dbPlanOpt = dailyRepo.findByUserIdAndPlanDate(userId, currentDate);

            if (dbPlanOpt.isPresent()) {
                dayDto.hasConflict = true;

                // Fetch saved data from DB
                List<MealItem> dbItems = itemRepo.findByMealPlanId(dbPlanOpt.get().getMealPlanId());
                List<WeeklyPlanDTO.Meal> savedMeals = new ArrayList<>();
                for (MealItem item : dbItems) {
                    WeeklyPlanDTO.Meal m = new WeeklyPlanDTO.Meal();
                    m.recipeId = item.getRecipeId();
                    m.recipeName = item.getCustomDishName();
                    m.calories = item.getCalories().intValue();
                    m.type = item.getMealTimeType().name();
                    savedMeals.add(m);
                }
                int savedCal = dbPlanOpt.get().getTotalCalories().intValue();

                // 3. Swap Logic
                if (sourceMode.equals("NEW_MANUAL")) {
                    // Manual Mode: Prioritize SAVED (OLD)
                    dayDto.currentSource = "SAVED_DB";

                    // Push NEW (Empty) to Alternate
                    dayDto.altMeals = dayDto.meals; // These are the empty slots created in manualStart
                    dayDto.altTotalCalories = dayDto.totalCalories;

                    // Set OLD to Current Display
                    dayDto.meals = savedMeals;
                    dayDto.totalCalories = savedCal;

                } else {
                    // AI Mode: Prioritize NEW (AI)
                    dayDto.currentSource = "NEW_AI";

                    // Push OLD (Saved) to Alternate
                    dayDto.altMeals = savedMeals;
                    dayDto.altTotalCalories = savedCal;
                }

                // [CRITICAL FIX] Convert altMeals to JSON string immediately
                try {
                    dayDto.altMealsJsonString = mapper.writeValueAsString(dayDto.altMeals);
                } catch (Exception e) {
                    dayDto.altMealsJsonString = "[]";
                }
            } else {
                // No Conflict
                dayDto.hasConflict = false;
                dayDto.currentSource = sourceMode;
                dayDto.altMealsJsonString = "[]"; // Initialize empty JSON for safety
            }
        }
    }

    private WeeklyPlanDTO.Meal createEmptyMeal(String type) {
        WeeklyPlanDTO.Meal meal = new WeeklyPlanDTO.Meal();
        meal.type = type;
        meal.recipeName = "Drag a recipe here";
        meal.calories = 0;
        meal.recipeId = null;
        return meal;
    }

    @PostMapping("/save")
    @ResponseBody // Bắt buộc để trả về JSON
    public ResponseEntity<?> savePlan(@RequestBody SavePlanDTO planDto,
            @AuthenticationPrincipal CustomUserDetails user) {

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "User not logged in"));
        }

        try {
            // Gọi Service để lưu vào DB
            mealPlanService.saveWeeklyPlan(user.getUserId(), planDto);

            return ResponseEntity.ok(Map.of("message", "Plan saved successfully!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Error saving plan: " + e.getMessage()));
        }
    }

    @GetMapping("/history/{date}")
    public String viewHistoryPlan(@PathVariable LocalDate date,
            HttpSession session,
            @AuthenticationPrincipal CustomUserDetails user) {

        // 1. Gọi Service lấy dữ liệu từ DB và map ngược lại thành WeeklyPlanDTO
        WeeklyPlanDTO plan = mealPlanService.getPlanByDate(user.getUserId(), date);

        if (plan == null) {
            return "redirect:/meal-plan/generate";
        }

        session.setAttribute("currentWeeklyPlan", plan);
        session.setAttribute("planMode", "EDIT"); // Đánh dấu là đang sửa

        return "redirect:/meal-plan/customize";
    }
}
