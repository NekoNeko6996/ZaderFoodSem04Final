package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.DailyMealPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.query.Param;

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
    
    @Query("SELECT COUNT(d) FROM DailyMealPlan d WHERE d.createdAt >= :startDate")
    long countNewMealPlans(@Param("startDate") LocalDateTime startDate);

    // [Biểu đồ] Đếm Meal Plan theo ngày (cho Line Chart)
    @Query("SELECT CAST(d.createdAt AS date), COUNT(d) FROM DailyMealPlan d " +
           "WHERE d.createdAt >= :startDate " +
           "GROUP BY CAST(d.createdAt AS date) ORDER BY CAST(d.createdAt AS date)")
    List<Object[]> countMealPlansByDate(@Param("startDate") LocalDateTime startDate);
    
    List<DailyMealPlan> findByUserIdAndPlanDateBetween(Integer userId, LocalDate startDate, LocalDate endDate);

    List<DailyMealPlan> findByUserIdAndPlanDateGreaterThanEqual(Integer userId, LocalDate date);
    List<DailyMealPlan> findByUserIdAndPlanDateLessThanEqualOrderByPlanDateDesc(Integer userId, LocalDate date);
    List<DailyMealPlan> findByUserIdAndPlanDateGreaterThanEqualOrderByPlanDateAsc(Integer userId, LocalDate date);
}