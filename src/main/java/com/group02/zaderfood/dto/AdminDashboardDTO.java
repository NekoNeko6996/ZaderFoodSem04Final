package com.group02.zaderfood.dto;

import lombok.Data;
import java.util.List;

@Data
public class AdminDashboardDTO {

    // 4 Thẻ thống kê chính
    private long totalUsers;
    private long newUsersThisMonth;

    private long activeRecipes;
    private long pendingRecipes;

    private long totalMealPlans;
    private long newMealPlansThisMonth; // Để tính % tăng trưởng

    private long totalAiTokens;

    // Dữ liệu biểu đồ (Chart.js)
    // Line Chart: User & Meal Plan 7 ngày qua
    private List<String> chartLabels; // ["Mon", "Tue", "Wed"...]
    private List<Long> chartNewUserData; // [12, 19, 3...]
    private List<Long> chartMealPlanData; // [5, 10, 2...]

    // Doughnut Chart: Diet Trends
    private List<String> dietLabels; // ["KETO", "VEGAN"...]
    private List<Long> dietData; // [50, 30...]

    private List<String> goalLabels;      // ["Weight Loss", "Muscle Gain"...]
    private List<Long> goalData;          // [500, 200...]

    private List<String> difficultyLabels; // ["Easy", "Hard"...]
    private List<Long> difficultyData;

// Top Recipes (Tạo class con hoặc dùng List<Map>)
    private List<TopRecipeDTO> topRecipes;

    @Data
    public static class TopRecipeDTO {

        private String name;
        private Double rating;
        private Long reviewCount;
    }
}
