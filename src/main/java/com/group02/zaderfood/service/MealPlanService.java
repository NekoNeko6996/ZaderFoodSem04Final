package com.group02.zaderfood.service;

import com.group02.zaderfood.dto.SavePlanDTO;
import com.group02.zaderfood.entity.*;
import com.group02.zaderfood.entity.enums.MealType;
import com.group02.zaderfood.entity.enums.PlanStatus;
import com.group02.zaderfood.repository.DailyMealPlanRepository;
import com.group02.zaderfood.repository.MealItemRepository;
import com.group02.zaderfood.repository.RecipeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class MealPlanService {

    @Autowired
    private DailyMealPlanRepository dailyRepo;

    @Autowired
    private MealItemRepository itemRepo;

    @Autowired
    private RecipeRepository recipeRepo;

    // Inject RecipeService để dùng lại hàm tính toán (Nếu RecipeService là Bean)
    @Autowired
    private RecipeService recipeService;

    @Transactional
    public void saveWeeklyPlan(Integer userId, SavePlanDTO dto) {
        LocalDate startDate = LocalDate.now().plusDays(1);

        int dayOffset = 0;
        for (SavePlanDTO.DayPlan dayDto : dto.days) {
            LocalDate planDate = startDate.plusDays(dayOffset++);

            // Biến tổng cho ngày
            BigDecimal dailyProtein = BigDecimal.ZERO;
            BigDecimal dailyCarbs = BigDecimal.ZERO;
            BigDecimal dailyFat = BigDecimal.ZERO;

            // 1. Tạo Header ngày
            DailyMealPlan dailyPlan = DailyMealPlan.builder()
                    .userId(userId)
                    .planDate(planDate)
                    .totalCalories(BigDecimal.valueOf(dayDto.totalCalories))
                    .status(PlanStatus.PLANNED)
                    .isGeneratedByAI(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            dailyPlan = dailyRepo.save(dailyPlan);

            // 2. Lưu món ăn & Cộng dồn dinh dưỡng
            if (dayDto.meals != null) {
                int orderIndex = 1;
                for (SavePlanDTO.MealItemDTO mealDto : dayDto.meals) {
                    if (mealDto.recipeName == null || mealDto.recipeName.isEmpty()) {
                        continue;
                    }

                    MealItem item = MealItem.builder()
                            .mealPlanId(dailyPlan.getMealPlanId())
                            .recipeId(mealDto.recipeId)
                            .customDishName(mealDto.recipeName)
                            .calories(BigDecimal.valueOf(mealDto.calories))
                            .mealTimeType(mapMealType(mealDto.type))
                            .quantityMultiplier(BigDecimal.ONE)
                            .orderIndex(orderIndex++)
                            .createdAt(LocalDateTime.now())
                            .build();

                    itemRepo.save(item);

                    // --- TÍNH TOÁN DINH DƯỠNG ---
                    if (mealDto.recipeId != null) {
                        // Tìm recipe đầy đủ (bao gồm ingredients)
                        Recipe r = recipeRepo.findById(mealDto.recipeId).orElse(null);

                        if (r != null) {
                            // Gọi hàm tính toán của RecipeService
                            // Hàm này sẽ điền giá trị vào các biến @Transient (protein, carbs, fat) của r
                            recipeService.calculateRecipeMacros(r);

                            // Cộng dồn vào tổng ngày
                            if (r.getProtein() != null) {
                                dailyProtein = dailyProtein.add(r.getProtein());
                            }
                            if (r.getCarbs() != null) {
                                dailyCarbs = dailyCarbs.add(r.getCarbs());
                            }
                            if (r.getFat() != null) {
                                dailyFat = dailyFat.add(r.getFat());
                            }
                        }
                    }
                }
            }

            // 3. Cập nhật lại DailyPlan với tổng dinh dưỡng
            dailyPlan.setTotalProtein(dailyProtein);
            dailyPlan.setTotalCarbs(dailyCarbs);
            dailyPlan.setTotalFat(dailyFat);

            dailyRepo.save(dailyPlan);
        }
    }

    private MealType mapMealType(String typeStr) {
        if (typeStr == null) {
            return MealType.BREAKFAST;
        }
        try {
            return MealType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MealType.BREAKFAST;
        }
    }
}
