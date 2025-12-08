package com.group02.zaderfood.dto;

import java.time.LocalDate;

public class CalendarDayDTO {
    public int dayValue;        // Ngày trong tháng (1, 2, ..., 31)
    public LocalDate fullDate;  // Ngày đầy đủ
    public boolean hasPlan;     // Có plan hay không
    public int totalCalories;   // Calo thực tế
    public String statusColor;  // "GRAY", "GREEN", "YELLOW", "RED", "NONE"
    public boolean isToday;     // Để highlight ngày hiện tại

    // Constructor nhanh
    public CalendarDayDTO(int dayValue, LocalDate fullDate) {
        this.dayValue = dayValue;
        this.fullDate = fullDate;
        this.statusColor = "NONE";
    }
}