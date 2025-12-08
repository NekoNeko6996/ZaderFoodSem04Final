package com.group02.zaderfood.service;

import com.group02.zaderfood.dto.DayDetailDTO;
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
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        LocalDate defaultStartDate = LocalDate.now().plusDays(1);
        int dayOffset = 0;

        for (SavePlanDTO.DayPlan dayDto : dto.days) {
            // 1. XÁC ĐỊNH NGÀY
            LocalDate planDate;
            try {
                planDate = parseDateFromLabel(dayDto.dayName);
            } catch (Exception e) {
                planDate = defaultStartDate.plusDays(dayOffset++);
            }

            // 2. XỬ LÝ DAILY MEAL PLAN (UPDATE HOẶC INSERT)
            DailyMealPlan dailyPlan;
            Optional<DailyMealPlan> existingPlanOpt = dailyRepo.findByUserIdAndPlanDate(userId, planDate);

            if (existingPlanOpt.isPresent()) {
                // CASE: UPDATE (Giữ nguyên ID cũ)
                dailyPlan = existingPlanOpt.get();
                dailyPlan.setTotalCalories(BigDecimal.valueOf(dayDto.totalCalories));
                dailyPlan.setUpdatedAt(LocalDateTime.now());
                // Không xóa, chỉ update thông tin cần thiết
            } else {
                // CASE: INSERT MỚI
                dailyPlan = DailyMealPlan.builder()
                        .userId(userId)
                        .planDate(planDate)
                        .totalCalories(BigDecimal.valueOf(dayDto.totalCalories))
                        .status(PlanStatus.PLANNED)
                        .isGeneratedByAI(true)
                        .createdAt(LocalDateTime.now())
                        .build();
            }

            // Lưu DailyPlan (Save sẽ tự hiểu update nếu có ID, insert nếu chưa)
            dailyPlan = dailyRepo.save(dailyPlan);

            // 3. TÍNH TOÁN MACROS TỔNG HỢP
            BigDecimal dailyProtein = BigDecimal.ZERO;
            BigDecimal dailyCarbs = BigDecimal.ZERO;
            BigDecimal dailyFat = BigDecimal.ZERO;

            // 4. XỬ LÝ MEAL ITEMS (SMART UPDATE)
            if (dayDto.meals != null) {
                // Lấy danh sách cũ từ DB để so sánh
                List<MealItem> dbItems = itemRepo.findByMealPlanId(dailyPlan.getMealPlanId());

                // Map để tra cứu nhanh theo ID
                Map<Integer, MealItem> dbItemMap = dbItems.stream()
                        .collect(Collectors.toMap(MealItem::getMealItemId, item -> item));

                List<Integer> processedIds = new ArrayList<>(); // Danh sách ID đã được xử lý
                int orderIndex = 1;

                for (SavePlanDTO.MealItemDTO mealDto : dayDto.meals) {
                    if (mealDto.recipeName == null || mealDto.recipeName.isEmpty()) {
                        continue;
                    }

                    MealItem itemToSave;

                    // A. KIỂM TRA CÓ ID GỬI LÊN KHÔNG?
                    if (mealDto.mealItemId != null && dbItemMap.containsKey(mealDto.mealItemId)) {
                        // UPDATE: Lấy entity cũ ra sửa
                        itemToSave = dbItemMap.get(mealDto.mealItemId);
                        processedIds.add(mealDto.mealItemId); // Đánh dấu ID này còn dùng
                    } else {
                        // INSERT: Tạo mới
                        itemToSave = new MealItem();
                        itemToSave.setMealPlanId(dailyPlan.getMealPlanId());
                        itemToSave.setCreatedAt(LocalDateTime.now());
                        itemToSave.setIsCustomEntry(false);
                        itemToSave.setIsDeleted(false);
                    }

                    // B. GÁN DỮ LIỆU MỚI
                    itemToSave.setRecipeId(mealDto.recipeId);
                    itemToSave.setCustomDishName(mealDto.recipeName);
                    itemToSave.setCalories(BigDecimal.valueOf(mealDto.calories));
                    itemToSave.setMealTimeType(mapMealType(mealDto.type));
                    itemToSave.setQuantityMultiplier(BigDecimal.ONE);
                    itemToSave.setOrderIndex(orderIndex++);
                    itemToSave.setUpdatedAt(LocalDateTime.now());

                    itemRepo.save(itemToSave);

                    // C. CỘNG DỒN MACROS
                    if (mealDto.recipeId != null) {
                        Recipe r = recipeRepo.findById(mealDto.recipeId).orElse(null);
                        if (r != null) {
                            recipeService.calculateRecipeMacros(r);
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

                // D. DELETE: Xóa những item cũ không còn trong danh sách mới
                for (MealItem dbItem : dbItems) {
                    if (!processedIds.contains(dbItem.getMealItemId())) {
                        itemRepo.delete(dbItem);
                    }
                }
            }

            // 5. CẬP NHẬT MACROS CHO DAILY PLAN
            dailyPlan.setTotalProtein(dailyProtein);
            dailyPlan.setTotalCarbs(dailyCarbs);
            dailyPlan.setTotalFat(dailyFat);
            dailyRepo.save(dailyPlan);
        }
    }

    // Helper: Parse chuỗi "Friday 06/12" thành LocalDate
    public LocalDate parseDateFromLabel(String label) {
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
            dayDto.dateString = dp.getPlanDate().toString();

            // [FIX 1: ĐƯA RA NGOÀI VÒNG LẶP]
            // Gán giá trị mặc định cho ngày, kể cả khi ngày đó chưa có món ăn nào
            dayDto.currentSource = "SAVED_DB";
            dayDto.hasConflict = true;
            dayDto.altMealsJsonString = "[]";

            List<MealItem> items = itemRepo.findByMealPlanId(dp.getMealPlanId());
            for (MealItem item : items) {
                // Kiểm tra an toàn
                if (item == null) {
                    continue;
                }

                WeeklyPlanDTO.Meal mealDto = new WeeklyPlanDTO.Meal();
                mealDto.mealItemId = item.getMealItemId();
                mealDto.recipeId = item.getRecipeId();
                mealDto.recipeName = item.getCustomDishName();
                mealDto.calories = item.getCalories().intValue();

                // [FIX 2: Code gọn gàng hơn, xóa dòng thừa gây lỗi]
                mealDto.type = (item.getMealTimeType() != null) ? item.getMealTimeType().name() : "BREAKFAST";

                dayDto.meals.add(mealDto);

                // (XÓA các dòng gán dayDto.currentSource ở đây đi)
            }
            dto.days.add(dayDto);
        }
        return dto;
    }

    public DayDetailDTO getDayDetail(Integer userId, LocalDate date) {
        // 1. Tìm Plan của ngày
        DailyMealPlan dailyPlan = dailyRepo.findByUserIdAndPlanDate(userId, date).orElse(null);
        if (dailyPlan == null) {
            return null;
        }

        DayDetailDTO dto = new DayDetailDTO();
        dto.date = date;
        dto.dayName = date.format(DateTimeFormatter.ofPattern("EEEE dd/MM"));
        dto.totalCalories = dailyPlan.getTotalCalories().intValue();
        // Lấy macros (xử lý null an toàn)
        dto.totalProtein = dailyPlan.getTotalProtein() != null ? dailyPlan.getTotalProtein().intValue() : 0;
        dto.totalCarbs = dailyPlan.getTotalCarbs() != null ? dailyPlan.getTotalCarbs().intValue() : 0;
        dto.totalFat = dailyPlan.getTotalFat() != null ? dailyPlan.getTotalFat().intValue() : 0;

        // 2. Lấy danh sách món ăn
        List<MealItem> items = itemRepo.findByMealPlanId(dailyPlan.getMealPlanId());
        dto.meals = new ArrayList<>();

        // Map tạm để cộng dồn nguyên liệu đi chợ (Optional - làm sau nếu phức tạp)
        // Map<String, String> shoppingMap = new HashMap<>(); 
        for (MealItem item : items) {
            DayDetailDTO.MealDetail mealDetail = new DayDetailDTO.MealDetail();
            mealDetail.type = item.getMealTimeType().name();
            mealDetail.recipeName = item.getCustomDishName();
            mealDetail.calories = item.getCalories().intValue();

            // Nếu có Recipe ID, lấy thêm ảnh và hướng dẫn
            if (item.getRecipeId() != null) {
                Recipe r = recipeRepo.findById(item.getRecipeId()).orElse(null);
                if (r != null) {
                    mealDetail.imageUrl = r.getImageUrl();
                    mealDetail.prepTime = r.getPrepTimeMin() != null ? r.getPrepTimeMin() : 0;
                    mealDetail.cookTime = r.getCookTimeMin() != null ? r.getCookTimeMin() : 0;

                    // Lấy Steps (Cần RecipeStepRepository hoặc truy cập qua quan hệ OneToMany nếu đã fetch EAGER/Transactional)
                    // mealDetail.steps = r.getRecipeSteps().stream().map(RecipeStep::getInstruction).collect(Collectors.toList());
                }
            } else {
                mealDetail.imageUrl = "/images/default-food.png";
            }
            dto.meals.add(mealDetail);
        }

        // Sắp xếp thứ tự bữa ăn
        // ... (Logic sort giống JavaScript) ...
        return dto;
    }
}
