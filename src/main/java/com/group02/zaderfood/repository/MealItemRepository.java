package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.MealItem;
import com.group02.zaderfood.entity.enums.MealType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MealItemRepository extends JpaRepository<MealItem, Integer> {
    
    // Tìm các món ăn thuộc về một DailyPlan cụ thể
    List<MealItem> findByMealPlanId(Integer mealPlanId);
    
    List<MealItem> findByMealPlanIdIn(List<Integer> mealPlanIds);
    
    boolean existsByMealPlanIdAndRecipeIdAndMealTimeType(Integer mealPlanId, Integer recipeId, MealType mealTimeType);
}