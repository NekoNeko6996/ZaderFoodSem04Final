package com.group02.zaderfood.dto;

import java.time.LocalDateTime;
import lombok.Data;
import java.util.List;

@Data
public class NutritionistDashboardDTO {
    // 1. Các thẻ số liệu (Top Cards)
    private long pendingRecipes;       // Số công thức chờ duyệt (Công việc chính)
    private long totalIngredients;     // Tổng số nguyên liệu trong kho
    private long activeRecipes;        // Công thức đang hoạt động
    private long totalReviews;         // Tổng số đánh giá từ người dùng (để biết phản hồi)
    
    private long totalMealPlans;
    private long pendingIngredients;
    private List<TopRecipeStat> topAddedRecipes;

    // 2. Biểu đồ 1: Xu hướng ăn kiêng của User (Pie Chart)
    // Để biết User đang chuộng Keto, Vegan hay Eat Clean...
    private List<String> dietLabels;
    private List<Long> dietData;

    // 3. Biểu đồ 2: Phân bố mục tiêu sức khỏe (Bar Chart)
    // Để biết bao nhiêu người muốn Giảm cân vs Tăng cơ -> Quyết định làm món gì
    private List<String> goalLabels;
    private List<Long> goalData;
    
    private List<TopRecipeStat> topFavoritedRecipes;

    public List<TopRecipeStat> getTopFavoritedRecipes() {
        return topFavoritedRecipes;
    }

    public void setTopFavoritedRecipes(List<TopRecipeStat> topFavoritedRecipes) {
        this.topFavoritedRecipes = topFavoritedRecipes;
    }
    
    
    public static class TopRecipeStat {
        private Integer id;          // [NEW] ID để tạo link
        private String name;
        private String imageUrl;     // [NEW] Ảnh
        private LocalDateTime createdAt; // [NEW] Ngày tạo
        private Long count;

        public TopRecipeStat(Integer id, String name, String imageUrl, LocalDateTime createdAt, Long count) {
            this.id = id;
            this.name = name;
            this.imageUrl = imageUrl;
            this.createdAt = createdAt;
            this.count = count;
        }

        // Getters
        public Integer getId() { return id; }
        public String getName() { return name; }
        public String getImageUrl() { return imageUrl; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public Long getCount() { return count; }
    }
}