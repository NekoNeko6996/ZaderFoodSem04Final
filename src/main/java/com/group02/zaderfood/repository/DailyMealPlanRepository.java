package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.DailyMealPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyMealPlanRepository extends JpaRepository<DailyMealPlan, Integer> {

    // 1. Lấy 5 plan mới nhất (Dựa vào ngày lên lịch)
    List<DailyMealPlan> findTop5ByUserIdOrderByPlanDateDesc(Integer userId);

    // 2. Lấy plan trong khoảng ngày (Dùng JPQL)
    @Query("SELECT d FROM DailyMealPlan d WHERE d.userId = :userId AND d.planDate >= :startDate AND d.planDate <= :endDate ORDER BY d.planDate ASC")
    List<DailyMealPlan> findByUserIdAndDateRange(Integer userId, LocalDate startDate, LocalDate endDate);

    // 3. Tìm chính xác 1 ngày để check tồn tại khi lưu đè
    Optional<DailyMealPlan> findByUserIdAndPlanDate(Integer userId, LocalDate planDate);
    
    List<DailyMealPlan> findByUserIdOrderByPlanDateDesc(Integer userId);
}