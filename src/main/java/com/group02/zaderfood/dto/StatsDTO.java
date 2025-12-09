package com.group02.zaderfood.dto;

import java.util.List;

public class StatsDTO {
    // 1. Số liệu tổng quan (Overview)
    public int totalTrackedDays;
    public int currentStreak;
    public double avgDailyCalories;
    public int adherenceScore; // Điểm tuân thủ (0-100%)

    // 2. Dữ liệu cho Biểu đồ Calo (Line Chart)
    public List<String> chartLabels; // Danh sách ngày (VD: "01/12", "02/12"...)
    public List<Integer> chartDataCalories; // Calo thực tế
    public List<Integer> chartDataGoal;     // Calo mục tiêu

    // 3. Dữ liệu cho Biểu đồ Macros (Doughnut Chart) - Trung bình
    public int avgProtein;
    public int avgCarbs;
    public int avgFat;

    // 4. Lời nhắc/Nhận xét (Insights)
    public List<String> insights; 
    public String overallStatus; // "Excellent", "Good", "Needs Improvement"
}