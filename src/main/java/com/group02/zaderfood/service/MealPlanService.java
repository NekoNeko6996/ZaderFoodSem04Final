package com.group02.zaderfood.service;

import com.group02.zaderfood.dto.SavePlanDTO;
import com.group02.zaderfood.dto.WeeklyPlanDTO;
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
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        // Mặc định ngày bắt đầu là ngày mai (nếu AI trả về thứ chung chung)
        LocalDate defaultStartDate = LocalDate.now().plusDays(1);
        int dayOffset = 0;

        for (SavePlanDTO.DayPlan dayDto : dto.days) {
            // 1. XÁC ĐỊNH NGÀY (PlanDate)
            LocalDate planDate;
            try {
                // Cố gắng parse chuỗi "Friday 06/12"
                planDate = parseDateFromLabel(dayDto.dayName);
            } catch (Exception e) {
                // Fallback: Nếu không parse được (VD: "Monday"), dùng logic cộng dồn ngày
                planDate = defaultStartDate.plusDays(dayOffset++);
            }

            // 2. XỬ LÝ LƯU ĐÈ (Overwrite)
            // Kiểm tra xem user đã có plan cho ngày này chưa
            Optional<DailyMealPlan> existingPlan = dailyRepo.findByUserIdAndPlanDate(userId, planDate);

            if (existingPlan.isPresent()) {
                // Nếu có, xóa các món ăn cũ của plan đó đi
                // (Lưu ý: Bạn cần thêm method deleteByMealPlanId trong MealItemRepository hoặc dùng logic dưới)
                List<MealItem> oldItems = itemRepo.findByMealPlanId(existingPlan.get().getMealPlanId());
                itemRepo.deleteAll(oldItems);

                // Xóa luôn header plan cũ để tạo mới cho sạch (hoặc update tùy logic, ở đây chọn xóa lưu mới)
                dailyRepo.delete(existingPlan.get());
                dailyRepo.flush(); // Đẩy lệnh xóa xuống DB ngay
            }

            // 3. TẠO PLAN MỚI
            // Biến tổng dinh dưỡng
            BigDecimal dailyProtein = BigDecimal.ZERO;
            BigDecimal dailyCarbs = BigDecimal.ZERO;
            BigDecimal dailyFat = BigDecimal.ZERO;

            DailyMealPlan dailyPlan = DailyMealPlan.builder()
                    .userId(userId)
                    .planDate(planDate)
                    .totalCalories(BigDecimal.valueOf(dayDto.totalCalories))
                    .status(PlanStatus.PLANNED)
                    .isGeneratedByAI(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            dailyPlan = dailyRepo.save(dailyPlan);

            // 4. LƯU MÓN ĂN (Meal Items)
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

                    // Cộng dồn Macros
                    if (mealDto.recipeId != null) {
                        Recipe r = recipeRepo.findById(mealDto.recipeId).orElse(null);
                        if (r != null) {
                            recipeService.calculateRecipeMacros(r); // Tính toán on-the-fly
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

            // Update lại tổng Macros cho ngày
            dailyPlan.setTotalProtein(dailyProtein);
            dailyPlan.setTotalCarbs(dailyCarbs);
            dailyPlan.setTotalFat(dailyFat);
            dailyRepo.save(dailyPlan);
        }
    }

    // Helper: Parse chuỗi "Friday 06/12" thành LocalDate
    private LocalDate parseDateFromLabel(String label) {
        // Regex tìm mẫu "dd/MM"
        Pattern pattern = Pattern.compile("(\\d{1,2})/(\\d{1,2})");
        Matcher matcher = pattern.matcher(label);

        if (matcher.find()) {
            int day = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int year = Year.now().getValue();

            // Xử lý logic qua năm (Ví dụ: Đang tháng 12, lập lịch cho tháng 1)
            LocalDate now = LocalDate.now();
            if (month < now.getMonthValue() && now.getMonthValue() == 12) {
                year++;
            }
            return LocalDate.of(year, month, day);
        }
        throw new IllegalArgumentException("Cannot parse date from label");
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

    public List<DailyMealPlan> getRecentPlans(Integer userId) {
        // Code ví dụ lấy 5 plan mới nhất
        return dailyRepo.findTop5ByUserIdOrderByPlanDateDesc(userId);
    }

    public WeeklyPlanDTO getPlanByDate(Integer userId, LocalDate startDate) {
        // Logic: Lấy 7 ngày liên tiếp từ ngày start
        List<DailyMealPlan> dbPlans = dailyRepo.findByUserIdAndDateRange(userId, startDate, startDate.plusDays(6));

        WeeklyPlanDTO dto = new WeeklyPlanDTO();
        dto.days = new ArrayList<>();

        for (DailyMealPlan dp : dbPlans) {
            WeeklyPlanDTO.DailyPlan dayDto = new WeeklyPlanDTO.DailyPlan();
            dayDto.dayName = dp.getPlanDate().format(DateTimeFormatter.ofPattern("EEEE dd/MM"));
            dayDto.totalCalories = dp.getTotalCalories().intValue();
            dayDto.meals = new ArrayList<>();

            List<MealItem> items = itemRepo.findByMealPlanId(dp.getMealPlanId());
            for (MealItem item : items) {
                WeeklyPlanDTO.Meal mealDto = new WeeklyPlanDTO.Meal();
                mealDto.recipeId = item.getRecipeId();
                mealDto.recipeName = item.getCustomDishName();
                mealDto.calories = item.getCalories().intValue();
                mealDto.type = item.getMealTimeType().name(); // Enum to String
                dayDto.meals.add(mealDto);
            }
            dto.days.add(dayDto);
        }
        return dto;
    }
}
