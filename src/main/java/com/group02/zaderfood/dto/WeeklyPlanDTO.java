package com.group02.zaderfood.dto;

import java.util.List;

public class WeeklyPlanDTO {
    public List<DailyPlan> days;

    public static class DailyPlan {
        public String dayName; // Monday, Tuesday...
        public int totalCalories;
        public List<Meal> meals;
    }

    public static class Meal {
        public String type; // Breakfast, Lunch, Dinner
        public String recipeName;
        public Integer recipeId;
        public int calories;
    }
}