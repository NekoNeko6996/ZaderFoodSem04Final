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

    public static class MealDetail {
        public Integer mealItemId; // [THÊM] ID để gửi request
        public String status;
        public String type;       // Breakfast, Lunch...
        public String recipeName;
        public int calories;
        public String imageUrl;
        public int prepTime;
        public int cookTime;
        public List<String> ingredients; // Nguyên liệu riêng món này
        public List<String> steps;       // Hướng dẫn nấu
    }
}