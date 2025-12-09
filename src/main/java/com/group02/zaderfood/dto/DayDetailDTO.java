package com.group02.zaderfood.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class DayDetailDTO {

    public LocalDate date;
    public String dayName;      // "Monday 08/12"
    public int totalCalories;
    public int totalProtein;
    public int totalCarbs;
    public int totalFat;

    // Danh sách món ăn chi tiết
    public List<MealDetail> meals;

    // Danh sách nguyên liệu tổng hợp cần mua cho ngày này
    public Map<String, String> shoppingList; // Tên nguyên liệu -> Số lượng (VD: "Trứng" -> "2 quả")

    public Integer consumedCalories;
    public Integer consumedProtein;
    public Integer consumedCarbs;
    public Integer consumedFat;

    public int totalMeals;
    public int eatenMeals;
    public int skippedMeals;
    
    public List<IngredientSummary> dailyIngredients;
    
    public List<MealDetail> suggestions;

    public static class MealDetail {
        public int protein;
        public int carbs;
        public int fat;

        public Integer mealItemId;
        public String status;
        public String type;
        public String recipeName;
        public int calories;
        public String imageUrl;
        public int prepTime;
        public int cookTime;
    }

    // [THÊM MỚI] Class con để hiển thị dòng nguyên liệu
    public static class IngredientSummary {

        public String name;
        public String quantity;
        public String imageUrl;
        public String category;

        public IngredientSummary(String name, String quantity, String imageUrl, String category) {
            this.name = name;
            this.quantity = quantity;
            this.imageUrl = imageUrl;
            this.category = category;
        }
    }
}
