package com.group02.zaderfood.service;

import com.group02.zaderfood.dto.CalendarDayDTO;
import com.group02.zaderfood.dto.DayDetailDTO;
import com.group02.zaderfood.dto.SavePlanDTO;
import com.group02.zaderfood.dto.StatsDTO;
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
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    public List<DailyMealPlan> getAllHistory(Integer userId) {
        return dailyRepo.findByUserIdOrderByPlanDateDesc(userId);
    }

    // 2. Hàm nhóm các ngày ăn theo Tuần (Để hiển thị Sidebar đẹp)
    public Map<String, List<DailyMealPlan>> groupPlansByWeek(List<DailyMealPlan> plans) {
        // Dùng LinkedHashMap để giữ thứ tự (Tuần mới nhất hiển thị trước)
        Map<String, List<DailyMealPlan>> grouped = new LinkedHashMap<>();

        // Định dạng tuần theo chuẩn (Ví dụ: "Week 49, 2025")
        WeekFields weekFields = WeekFields.of(Locale.getDefault());

        for (DailyMealPlan plan : plans) {
            LocalDate date = plan.getPlanDate();
            int weekNum = date.get(weekFields.weekOfWeekBasedYear());
            int year = date.get(weekFields.weekBasedYear());

            // Key đại diện cho nhóm: "Week 49 - 2025"
            String key = "Week " + weekNum + " - " + year;

            // Nếu chưa có key này thì tạo list mới, sau đó add plan vào
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(plan);
        }

        return grouped;
    }

    public List<CalendarDayDTO> getMonthlyCalendar(Integer userId, int month, int year, int calorieGoal) {
        List<CalendarDayDTO> calendarDays = new ArrayList<>();

        LocalDate firstDay = LocalDate.of(year, month, 1);
        int daysInMonth = firstDay.lengthOfMonth();

        // 1. Lấy tất cả Plan trong tháng đó của User
        LocalDate lastDay = firstDay.plusDays(daysInMonth - 1);
        List<DailyMealPlan> monthPlans = dailyRepo.findByUserIdAndDateRange(userId, firstDay, lastDay);

        // Map để tra cứu nhanh: Date -> Plan
        Map<LocalDate, DailyMealPlan> planMap = monthPlans.stream()
                .collect(Collectors.toMap(DailyMealPlan::getPlanDate, p -> p));

        // 2. Tạo dữ liệu cho từng ngày
        for (int i = 1; i <= daysInMonth; i++) {
            LocalDate currentDate = LocalDate.of(year, month, i);
            CalendarDayDTO dto = new CalendarDayDTO(i, currentDate);

            if (currentDate.equals(LocalDate.now())) {
                dto.isToday = true;
            }

            if (planMap.containsKey(currentDate)) {
                DailyMealPlan plan = planMap.get(currentDate);
                dto.hasPlan = true;
                int actualCal = plan.getTotalCalories().intValue();
                dto.totalCalories = actualCal;

                // --- LOGIC TÔ MÀU (Dựa trên % so với Goal) ---
                if (actualCal == 0) {
                    dto.statusColor = "GRAY"; // Đã lên lịch nhưng chưa có món/chưa ăn
                } else {
                    double ratio = (double) actualCal / calorieGoal;

                    if (ratio >= 0.9 && ratio <= 1.1) {
                        // Chênh lệch +/- 10% -> Tốt (XANH)
                        dto.statusColor = "GREEN";
                    } else if (ratio >= 0.8 && ratio <= 1.2) {
                        // Chênh lệch +/- 20% -> Khá (VÀNG)
                        dto.statusColor = "YELLOW";
                    } else {
                        // Chênh lệch quá nhiều -> Cảnh báo (ĐỎ)
                        dto.statusColor = "RED";
                    }
                }
            }
            calendarDays.add(dto);
        }
        return calendarDays;
    }

    // Hàm hỗ trợ tính số ô trống đầu tháng (Để lịch hiển thị đúng thứ)
    public int getStartDayOffset(int month, int year) {
        // Java: Monday=1 ... Sunday=7. 
        // Lịch của bạn Chủ Nhật đứng đầu (Sunday=0 trong logic render grid)
        LocalDate firstDay = LocalDate.of(year, month, 1);
        int dayOfWeek = firstDay.getDayOfWeek().getValue(); // 1(Mon) -> 7(Sun)

        // Nếu muốn Chủ Nhật là cột đầu tiên:
        if (dayOfWeek == 7) {
            return 0; // Chủ nhật không cần offset
        }
        return dayOfWeek; // Thứ 2 offset 1, Thứ 3 offset 2...
    }
    
    public StatsDTO calculateStats(Integer userId, int calorieGoal) {
        StatsDTO stats = new StatsDTO();
        
        // 1. Lấy dữ liệu 30 ngày gần nhất
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(29);
        List<DailyMealPlan> plans = dailyRepo.findByUserIdAndDateRange(userId, startDate, endDate);
        
        stats.totalTrackedDays = plans.size();
        stats.chartLabels = new ArrayList<>();
        stats.chartDataCalories = new ArrayList<>();
        stats.chartDataGoal = new ArrayList<>();
        stats.insights = new ArrayList<>();
        
        if (plans.isEmpty()) {
            stats.insights.add("Start tracking your meals to see analytics here!");
            return stats;
        }

        long totalCal = 0;
        long totalPro = 0, totalCarb = 0, totalFat = 0;
        int goodDays = 0;

        // 2. Duyệt qua từng ngày để xây dựng dữ liệu biểu đồ
        // Lưu ý: plans từ DB có thể không liên tục, cần xử lý nếu muốn biểu đồ liên tục
        // Ở đây ta làm đơn giản: chỉ vẽ những ngày có dữ liệu
        for (DailyMealPlan p : plans) {
            stats.chartLabels.add(p.getPlanDate().format(DateTimeFormatter.ofPattern("dd/MM")));
            stats.chartDataCalories.add(p.getTotalCalories().intValue());
            stats.chartDataGoal.add(calorieGoal); // Mục tiêu có thể đổi, nhưng lấy hiện tại cho đơn giản

            totalCal += p.getTotalCalories().intValue();
            totalPro += (p.getTotalProtein() != null) ? p.getTotalProtein().intValue() : 0;
            totalCarb += (p.getTotalCarbs() != null) ? p.getTotalCarbs().intValue() : 0;
            totalFat += (p.getTotalFat() != null) ? p.getTotalFat().intValue() : 0;

            // Kiểm tra tuân thủ (+/- 15%)
            double ratio = p.getTotalCalories().doubleValue() / calorieGoal;
            if (ratio >= 0.85 && ratio <= 1.15) goodDays++;
        }

        // 3. Tính trung bình
        stats.avgDailyCalories = (double) totalCal / plans.size();
        stats.avgProtein = (int) (totalPro / plans.size());
        stats.avgCarbs = (int) (totalCarb / plans.size());
        stats.avgFat = (int) (totalFat / plans.size());
        
        stats.adherenceScore = (goodDays * 100) / plans.size();

        // 4. Tạo Insights (Lời nhắc thông minh)
        if (stats.adherenceScore > 80) {
            stats.overallStatus = "Excellent";
            stats.insights.add("🔥 You're on fire! Consistency is key.");
        } else if (stats.adherenceScore > 50) {
            stats.overallStatus = "Good";
            stats.insights.add("👍 Doing well, but watch out for weekend spikes.");
        } else {
            stats.overallStatus = "Needs Focus";
            stats.insights.add("⚠️ You are frequently missing your calorie targets.");
        }

        // Check Macro
        if (stats.avgProtein < (calorieGoal * 0.2 / 4)) { // Ví dụ thấp hơn 20%
            stats.insights.add("🥩 Your protein intake is low. Try adding more chicken or beans.");
        }
        
        return stats;
    }
}
