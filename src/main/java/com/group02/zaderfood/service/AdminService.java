package com.group02.zaderfood.service;

import com.group02.zaderfood.dto.AdminDashboardDTO;
import com.group02.zaderfood.dto.NutritionistDashboardDTO;
import com.group02.zaderfood.entity.enums.RecipeStatus;
import com.group02.zaderfood.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AdminService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private DailyMealPlanRepository mealPlanRepository;
    @Autowired
    private AiRequestLogRepository aiLogRepository;
    @Autowired
    private UserDietaryPreferenceRepository dietRepo;
    @Autowired
    private UserProfileRepository profileRepo;
    @Autowired
    private ReviewRepository reviewRepo;
    @Autowired
    private IngredientRepository ingredientRepository;

    public AdminDashboardDTO getDashboardStats() {
        AdminDashboardDTO dto = new AdminDashboardDTO();
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0);
        LocalDateTime startOf7Days = LocalDateTime.now().minusDays(6).withHour(0).withMinute(0);

        // 1. Basic Stats
        dto.setTotalUsers(userRepository.count());
        dto.setNewUsersThisMonth(userRepository.countNewUsers(startOfMonth));

        dto.setActiveRecipes(recipeRepository.countByStatus(RecipeStatus.ACTIVE));
        dto.setPendingRecipes(recipeRepository.countByStatus(RecipeStatus.PENDING));

        dto.setTotalMealPlans(mealPlanRepository.count());
        dto.setNewMealPlansThisMonth(mealPlanRepository.countNewMealPlans(startOfMonth));

        Long tokens = aiLogRepository.getTotalTokensUsed();
        dto.setTotalAiTokens(tokens != null ? tokens : 0);

        // 2. Process Line Chart Data (7 ngày gần nhất)
        List<String> labels = new ArrayList<>();
        List<Long> userData = new ArrayList<>();
        List<Long> planData = new ArrayList<>();

        // Map dữ liệu từ DB vào Map<String, Long> để dễ xử lý ngày tháng
        Map<String, Long> userMap = convertToMap(userRepository.countUsersByDate(startOf7Days));
        Map<String, Long> planMap = convertToMap(mealPlanRepository.countMealPlansByDate(startOf7Days));

        // Loop 7 ngày để đảm bảo ngày nào không có dữ liệu thì value = 0
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
        for (int i = 6; i >= 0; i--) {
            LocalDateTime date = LocalDateTime.now().minusDays(i);
            String key = date.toLocalDate().toString(); // Key format YYYY-MM-DD từ SQL
            String label = date.format(formatter);      // Label hiển thị dd/MM

            labels.add(label);
            userData.add(userMap.getOrDefault(key, 0L));
            planData.add(planMap.getOrDefault(key, 0L));
        }

        dto.setChartLabels(labels);
        dto.setChartNewUserData(userData);
        dto.setChartMealPlanData(planData);

        // 3. Process Diet Chart
        List<Object[]> dietRaw = dietRepo.countByDietType();
        List<String> dLabels = new ArrayList<>();
        List<Long> dData = new ArrayList<>();

        for (Object[] row : dietRaw) {
            dLabels.add(row[0].toString());
            dData.add((Long) row[1]);
        }
        dto.setDietLabels(dLabels);
        dto.setDietData(dData);

        List<Object[]> goals = profileRepo.countUsersByGoal();
        List<String> gLabels = new ArrayList<>();
        List<Long> gData = new ArrayList<>();
        for (Object[] row : goals) {
            gLabels.add(row[0] != null ? row[0].toString() : "Unknown");
            gData.add((Long) row[1]);
        }
        dto.setGoalLabels(gLabels);
        dto.setGoalData(gData);

        // 2. Thống kê Top Recipes
        List<Object[]> top = reviewRepo.findTopRatedRecipes();
        List<AdminDashboardDTO.TopRecipeDTO> topList = new ArrayList<>();
        for (Object[] row : top) {
            AdminDashboardDTO.TopRecipeDTO item = new AdminDashboardDTO.TopRecipeDTO();
            item.setName((String) row[0]);
            item.setRating((Double) row[1]);
            item.setReviewCount((Long) row[2]);
            topList.add(item);
        }
        dto.setTopRecipes(topList);

        return dto;
    }

    // Helper: Convert List<Object[]> to Map<DateString, Count>
    private Map<String, Long> convertToMap(List<Object[]> rawData) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rawData) {
            String dateKey = row[0].toString(); // SQL Date trả về String YYYY-MM-DD
            Long count = (Long) row[1];
            map.put(dateKey, count);
        }
        return map;
    }

    public NutritionistDashboardDTO getNutritionistStats() {
        NutritionistDashboardDTO dto = new NutritionistDashboardDTO();

        // 1. Lấy số liệu tổng quan (Counters)
        dto.setPendingRecipes(recipeRepository.countByStatus(RecipeStatus.PENDING));
        dto.setTotalIngredients(ingredientRepository.count());
        dto.setActiveRecipes(recipeRepository.countByStatus(RecipeStatus.ACTIVE));
        // Giả sử có ReviewRepo
        // dto.setTotalReviews(reviewRepository.count()); 

        // 2. Xử lý dữ liệu biểu đồ Diet (Xu hướng ăn kiêng)
        List<Object[]> dietRaw = dietRepo.countByDietType();
        List<String> dLabels = new ArrayList<>();
        List<Long> dData = new ArrayList<>();

        for (Object[] row : dietRaw) {
            dLabels.add(row[0].toString()); // Tên Diet (KETO, VEGAN...)
            dData.add((Long) row[1]);       // Số lượng
        }
        dto.setDietLabels(dLabels);
        dto.setDietData(dData);

        // 3. Xử lý dữ liệu biểu đồ Goal (Mục tiêu)
        List<Object[]> goalRaw = profileRepo.countUsersByGoal();
        List<String> gLabels = new ArrayList<>();
        List<Long> gData = new ArrayList<>();

        for (Object[] row : goalRaw) {
            // Xử lý null hoặc tên đẹp hơn nếu cần
            String goalName = row[0] != null ? row[0].toString() : "Unknown";
            gLabels.add(goalName);
            gData.add((Long) row[1]);
        }
        dto.setGoalLabels(gLabels);
        dto.setGoalData(gData);

        return dto;
    }
}
