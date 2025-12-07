package com.group02.zaderfood.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

public class WeeklyPlanDTO {

    public List<DailyPlan> days;

    public static class DailyPlan {

        public String dayName;
        public String dateString;
        public int totalCalories;
        public List<Meal> meals;

        public boolean hasConflict;
        public String currentSource;

        public int altTotalCalories;
        public List<Meal> altMeals;

        public String altMealsJsonString;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meal {
        public Integer mealItemId;
        public String type;
        public Integer recipeId;
        public String recipeName;
        public int calories;
    }
}
