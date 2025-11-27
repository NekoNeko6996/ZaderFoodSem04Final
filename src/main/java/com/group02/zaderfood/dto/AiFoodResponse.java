package com.group02.zaderfood.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiFoodResponse {
    private String dishName;
    private int calories;
    private String protein;
    private String carbs;
    private String fat;
    private String time;
    private List<String> ingredients;
    private List<String> instructions;
    private String error;
}