package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.DailyMealPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyMealPlanRepository extends JpaRepository<DailyMealPlan, Integer> {
    
    // Tìm kế hoạch theo ngày và user (để tránh tạo trùng lặp sau này)
    List<DailyMealPlan> findByUserIdAndPlanDate(Integer userId, LocalDate planDate);
}