package com.group02.zaderfood.dto;

import java.math.BigDecimal;
import java.util.List;

public class SavePlanDTO {
    public List<DayPlan> days;

    public static class DayPlan {
        public String dayName;      // "Monday", "Tuesday"...
        public int totalCalories;
        public List<MealItemDTO> meals;
    }

    public static class MealItemDTO {
        public Integer mealItemId;
        public Integer recipeId;    // Có thể null nếu là món custom (tương lai)
        public String recipeName;
        public int calories;
        public String type;         // "Breakfast", "Lunch", "Dinner"
        private BigDecimal protein;
        private BigDecimal carbs;
        private BigDecimal fat;
    }
}