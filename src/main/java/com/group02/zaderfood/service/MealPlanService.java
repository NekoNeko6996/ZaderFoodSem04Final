package com.group02.zaderfood.service;

import com.group02.zaderfood.dto.CalendarDayDTO;
import com.group02.zaderfood.dto.DayDetailDTO;
import com.group02.zaderfood.dto.SavePlanDTO;
import com.group02.zaderfood.dto.StatsDTO;
import com.group02.zaderfood.dto.WeeklyPlanDTO;
import com.group02.zaderfood.entity.*;
import com.group02.zaderfood.entity.enums.MealType;
import com.group02.zaderfood.entity.enums.PlanStatus;
import com.group02.zaderfood.entity.enums.RecipeStatus;
import com.group02.zaderfood.repository.DailyMealPlanRepository;
import com.group02.zaderfood.repository.IngredientRepository;
import com.group02.zaderfood.repository.MealItemRepository;
import com.group02.zaderfood.repository.RecipeIngredientRepository;
import com.group02.zaderfood.repository.RecipeRepository;
import com.group02.zaderfood.repository.ShoppingListItemRepository;
import com.group02.zaderfood.repository.ShoppingListRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

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

    @Autowired
    private ShoppingListRepository shoppingListRepo;
    @Autowired
    private ShoppingListItemRepository shoppingItemRepo;
    @Autowired
    private RecipeIngredientRepository recipeIngRepo;

    @Autowired
    private IngredientRepository ingredientRepo;

    @Autowired
    private FileStorageService fileStorageService;

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
        dto.dayName = date.format(DateTimeFormatter.ofPattern("EEEE dd/MM", Locale.ENGLISH));
        
        dto.totalCalories = dailyPlan.getTotalCalories().intValue();
        // Lấy macros (xử lý null an toàn)
        dto.totalProtein = dailyPlan.getTotalProtein() != null ? dailyPlan.getTotalProtein().intValue() : 0;
        dto.totalCarbs = dailyPlan.getTotalCarbs() != null ? dailyPlan.getTotalCarbs().intValue() : 0;
        dto.totalFat = dailyPlan.getTotalFat() != null ? dailyPlan.getTotalFat().intValue() : 0;

        // 2. Lấy danh sách món ăn
        List<MealItem> items = itemRepo.findByMealPlanId(dailyPlan.getMealPlanId());
        dto.meals = new ArrayList<>();

        int consumedCal = 0;
        int consumedPro = 0, consumedCarb = 0, consumedFat = 0;
        int eatenCount = 0, skippedCount = 0;

        List<DayDetailDTO.IngredientSummary> ingredientList = new ArrayList<>();

        for (MealItem item : items) {
            DayDetailDTO.MealDetail mealDetail = new DayDetailDTO.MealDetail();
            mealDetail.mealItemId = item.getMealItemId();
            mealDetail.type = item.getMealTimeType().name();
            mealDetail.recipeName = item.getCustomDishName();
            mealDetail.calories = item.getCalories().intValue();
            mealDetail.status = item.getStatus();

            Recipe r = null;

            // 1. Lấy thông tin Recipe và Tự động tính Macros
            if (item.getRecipeId() != null) {
                r = recipeRepo.findById(item.getRecipeId()).orElse(null);

                // Lấy danh sách nguyên liệu (để làm Shopping List)
                List<RecipeIngredient> recipeIngredients = recipeIngRepo.findByRecipeId(item.getRecipeId());

                // --- SHOPPING LIST LOGIC (Giữ nguyên) ---
                for (RecipeIngredient ri : recipeIngredients) {
                    Ingredient ing = ingredientRepo.findById(ri.getIngredientId()).orElse(null);
                    String ingName = (ing != null) ? ing.getName() : "Unknown";
                    String ingImg = (ing != null && ing.getImageUrl() != null) ? ing.getImageUrl() : "/images/ingredients/default.png";
                    String category = "Pantry";

                    BigDecimal qty = (ri.getQuantity() != null) ? ri.getQuantity() : BigDecimal.ZERO;
                    if (item.getQuantityMultiplier() != null) {
                        qty = qty.multiply(item.getQuantityMultiplier());
                    }
                    String quantityStr = qty.stripTrailingZeros().toPlainString() + " " + ri.getUnit();

                    ingredientList.add(new DayDetailDTO.IngredientSummary(ingName, quantityStr, ingImg, category));
                }
                // ----------------------------------------

                if (r != null) {
                    // [QUAN TRỌNG] Gán danh sách nguyên liệu vào Recipe để Service có dữ liệu tính toán
                    r.setRecipeIngredients(recipeIngredients);

                    // [NEW] GỌI HÀM TÍNH TOÁN CỦA RECIPE SERVICE
                    // Hàm này sẽ điền giá trị vào r.protein, r.carbs, r.fat (Transient fields)
                    recipeService.calculateRecipeMacros(r);

                    mealDetail.imageUrl = r.getImageUrl();
                    mealDetail.prepTime = r.getPrepTimeMin() != null ? r.getPrepTimeMin() : 0;
                    mealDetail.cookTime = r.getCookTimeMin() != null ? r.getCookTimeMin() : 0;
                }

                // --- TÍNH TOÁN MACROS CHO TỪNG MÓN ---
                // 1. Protein
                BigDecimal p = item.getProtein();
                if (p == null && r != null) {
                    p = r.getProtein(); // Lấy từ Recipe nếu Item null
                }
                mealDetail.protein = (p != null) ? p.intValue() : 0;

                // 2. Carbs
                BigDecimal c = item.getCarbs();
                if (c == null && r != null) {
                    c = r.getCarbs();
                }
                mealDetail.carbs = (c != null) ? c.intValue() : 0;

                // 3. Fat
                BigDecimal f = item.getFat();
                if (f == null && r != null) {
                    f = r.getFat();
                }
                mealDetail.fat = (f != null) ? f.intValue() : 0;
            } else {
                mealDetail.imageUrl = item.getImageUrl() != null ? item.getImageUrl() : "/images/default-food.png";
            }

            // 2. TÍNH TOÁN DINH DƯỠNG THỰC TẾ (ĐÃ ĂN)
            if ("EATEN".equals(item.getStatus())) {
                eatenCount++;

                // CALORIES
                consumedCal += mealDetail.calories;

                // PROTEIN
                BigDecimal p = item.getProtein();
                // Nếu item không có, lấy từ Recipe (đã được calculateRecipeMacros điền số)
                if (p == null && r != null) {
                    p = r.getProtein();
                }
                consumedPro += (p != null) ? p.intValue() : 0;

                // CARBS
                BigDecimal c = item.getCarbs();
                if (c == null && r != null) {
                    c = r.getCarbs();
                }
                consumedCarb += (c != null) ? c.intValue() : 0;

                // FAT
                BigDecimal f = item.getFat();
                if (f == null && r != null) {
                    f = r.getFat();
                }
                consumedFat += (f != null) ? f.intValue() : 0;

            } else if ("SKIPPED".equals(item.getStatus())) {
                skippedCount++;
            }

            dto.meals.add(mealDetail);
        }

        dto.consumedCalories = consumedCal;
        dto.consumedProtein = consumedPro;
        dto.consumedCarbs = consumedCarb;
        dto.consumedFat = consumedFat;

        dto.totalMeals = items.size();
        dto.eatenMeals = eatenCount;
        dto.skippedMeals = skippedCount;

        dto.totalCalories = dailyPlan.getTotalCalories().intValue();
        dto.dailyIngredients = ingredientList;
        
        dto.suggestions = new ArrayList<>();

        int remainingCal = dto.totalCalories - consumedCal;
        
        // Chỉ gợi ý nếu còn thiếu năng lượng đáng kể (> 100kcal)
        if (remainingCal > 100) {

            // 1. Lấy danh sách ứng viên (Lấy 50 món Active bất kỳ)
            List<Recipe> candidates = recipeRepo.findTop50ByStatus(RecipeStatus.ACTIVE);

            List<Recipe> validRecipes = new ArrayList<>();

            // 2. Tính toán & Lọc
            for (Recipe r : candidates) {
                // Tự động tính toán Macros (điền vào @Transient protein, carbs, fat, totalCalories)
                // Lưu ý: Cần gán recipeIngredients trước nếu hàm calculate chưa tự fetch (như đã sửa ở bước trước)
                // Tuy nhiên hàm calculateRecipeMacros trong RecipeService của bạn đã có dòng recipe.getRecipeIngredients()
                // Do Hibernate Lazy Loading, nếu r lấy từ Repo thì list này có thể chưa load.
                // An toàn nhất là fetch Eager hoặc Transactional. Ở đây ta giả định Service có @Transactional.

                recipeService.calculateRecipeMacros(r);

                int rCal = (r.getTotalCalories() != null) ? r.getTotalCalories().intValue() : 0;

                // Điều kiện: Calo món ăn phải nhỏ hơn hoặc bằng số còn thiếu (cho phép lố tí xíu +50)
                if (rCal > 0 && rCal <= (remainingCal + 50)) {
                    validRecipes.add(r);
                }
            }

            // 3. Sắp xếp theo Protein giảm dần (Java Stream)
            List<Recipe> topPicks = validRecipes.stream()
                    .sorted((r1, r2) -> {
                        BigDecimal p1 = r1.getProtein() != null ? r1.getProtein() : BigDecimal.ZERO;
                        BigDecimal p2 = r2.getProtein() != null ? r2.getProtein() : BigDecimal.ZERO;
                        return p2.compareTo(p1); // Giảm dần
                    })
                    .limit(3) // Lấy 3 món tốt nhất
                    .collect(Collectors.toList());

            // 4. Map sang DTO
            for (Recipe r : topPicks) {
                DayDetailDTO.MealDetail suggestion = new DayDetailDTO.MealDetail();
                suggestion.recipeName = r.getName();
                suggestion.calories = r.getTotalCalories().intValue();
                suggestion.protein = r.getProtein() != null ? r.getProtein().intValue() : 0;
                suggestion.carbs = r.getCarbs() != null ? r.getCarbs().intValue() : 0;
                suggestion.fat = r.getFat() != null ? r.getFat().intValue() : 0;
                suggestion.imageUrl = r.getImageUrl();
                suggestion.prepTime = r.getPrepTimeMin();

                // Set type đặc biệt để Frontend nhận biết
                suggestion.type = "RECIPE_SUGGESTION";
                suggestion.mealItemId = r.getRecipeId(); // Mượn trường ID để chứa RecipeId

                dto.suggestions.add(suggestion);
            }
        }

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
            if (ratio >= 0.85 && ratio <= 1.15) {
                goodDays++;
            }
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

    // --- 1. LOGIC MARK AS EATEN ---
    @Transactional
    public void updateMealItemStatus(Integer itemId, String status) {
        MealItem item = itemRepo.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));

        // Kiểm tra status hợp lệ
        if (!status.equals("EATEN") && !status.equals("SKIPPED") && !status.equals("PENDING")) {
            throw new IllegalArgumentException("Invalid status");
        }

        item.setStatus(status);
        item.setUpdatedAt(LocalDateTime.now());
        itemRepo.save(item);

        // (Optional) Tính lại tổng thực tế của DailyPlan nếu cần thiết kế cache
    }

    // --- 2. LOGIC TẠO SHOPPING LIST ---
    @Transactional
    public void createShoppingListForDate(Integer userId, LocalDate date) {
        // A. Tìm Plan của ngày đó
        DailyMealPlan dailyPlan = dailyRepo.findByUserIdAndPlanDate(userId, date)
                .orElseThrow(() -> new RuntimeException("No meal plan found for this date"));

        // B. Tạo Shopping List Header
        ShoppingList list = new ShoppingList();
        list.setUserId(userId);
        list.setName("Shopping for " + date.toString());
        list.setFromDate(date);
        list.setToDate(date);
        list.setStatus("PENDING");
        list.setCreatedAt(LocalDateTime.now());
        list = shoppingListRepo.save(list);

        // C. Lấy tất cả món ăn trong ngày
        List<MealItem> mealItems = itemRepo.findByMealPlanId(dailyPlan.getMealPlanId());

        // D. Duyệt từng món -> Lấy công thức -> Lấy nguyên liệu -> Lưu vào Shopping List
        for (MealItem meal : mealItems) {
            if (meal.getRecipeId() != null) {
                List<RecipeIngredient> ingredients = recipeIngRepo.findByRecipeId(meal.getRecipeId());

                for (RecipeIngredient ri : ingredients) {
                    ShoppingListItem shopItem = new ShoppingListItem();
                    shopItem.setListId(list.getListId());
                    shopItem.setIngredientId(ri.getIngredientId());
                    shopItem.setQuantity(ri.getQuantity()); // Có thể nhân với meal.getQuantityMultiplier()
                    shopItem.setUnit(ri.getUnit());
                    shopItem.setIsBought(false);

                    shoppingItemRepo.save(shopItem);
                }
            }
        }
    }

    // --- 3. LOGIC THÊM SNACK (SMART ADD) ---
    @Transactional
    public boolean addSmartSnack(Integer userId, LocalDate date) {
        DailyMealPlan dailyPlan = dailyRepo.findByUserIdAndPlanDate(userId, date).orElse(null);
        if (dailyPlan == null) {
            return false;
        }

        // A. Tìm một công thức Snack ngẫu nhiên (hoặc logic AI phức tạp hơn)
        // Giả sử có hàm tìm Recipe theo Type
        // List<Recipe> snacks = recipeRepo.findByMealType("SNACK");
        // Recipe randomSnack = ...
        // Demo: Hardcode tìm recipe có ID cụ thể hoặc món đầu tiên là snack
        // Bạn cần viết query: SELECT * FROM Recipes WHERE Description LIKE '%snack%' hoặc có category snack
        // Ở đây tôi giả lập tạo một món Snack nhanh
        MealItem snack = new MealItem();
        snack.setMealPlanId(dailyPlan.getMealPlanId());
        snack.setCustomDishName("Healthy Yogurt & Nuts"); // Tên món
        snack.setCalories(BigDecimal.valueOf(150));       // Calo
        snack.setProtein(BigDecimal.valueOf(10));
        snack.setCarbs(BigDecimal.valueOf(15));
        snack.setFat(BigDecimal.valueOf(5));
        snack.setMealTimeType(MealType.SNACK);
        snack.setStatus("PENDING");
        snack.setCreatedAt(LocalDateTime.now());

        itemRepo.save(snack);
        return true;
    }

    public ByteArrayInputStream exportShoppingListToExcel(Integer userId, LocalDate date) throws Exception {
        // Lấy dữ liệu (Tái sử dụng logic getDayDetail hoặc tách hàm lấy ingredient riêng)
        DayDetailDTO dayDetail = getDayDetail(userId, date);
        List<DayDetailDTO.IngredientSummary> ingredients = dayDetail.dailyIngredients;

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Shopping List");

            // Header Row
            Row headerRow = sheet.createRow(0);
            String[] columns = {"Ingredient", "Category", "Quantity"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                CellStyle style = workbook.createCellStyle();
                Font font = workbook.createFont();
                font.setBold(true);
                style.setFont(font);
                cell.setCellStyle(style);
            }

            // Data Rows
            int rowIdx = 1;
            for (DayDetailDTO.IngredientSummary item : ingredients) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(item.name);
                row.createCell(1).setCellValue(item.category);
                row.createCell(2).setCellValue(item.quantity);
            }

            // Auto size cột
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Transactional
    public void updateMealItemDetails(Integer itemId, String name, BigDecimal cal, BigDecimal pro, BigDecimal carb, BigDecimal fat, MultipartFile image) {
        MealItem item = itemRepo.findById(itemId).orElseThrow(() -> new RuntimeException("Item not found"));

        item.setCustomDishName(name);
        item.setCalories(cal);
        item.setProtein(pro);
        item.setCarbs(carb);
        item.setFat(fat);

        // Nếu có ảnh mới thì update, không thì giữ nguyên
        if (image != null && !image.isEmpty()) {
            String imgUrl = fileStorageService.storeFile(image);
            item.setImageUrl(imgUrl);
        }

        // Ngắt kết nối với Recipe cũ vì giờ nó là món custom
        item.setRecipeId(null);
        item.setIsCustomEntry(true);
        item.setStatus("EATEN"); // Mặc định update xong coi như đã ăn (hoặc tùy logic bạn)
        item.setUpdatedAt(LocalDateTime.now());

        itemRepo.save(item);
    }

    @Transactional
    public void addCustomMealItem(Integer userId, LocalDate date, String mealTypeStr, String name, BigDecimal cal, BigDecimal pro, BigDecimal carb, BigDecimal fat, MultipartFile image) {
        // 1. Tìm hoặc tạo DailyPlan cho ngày đó
        DailyMealPlan dailyPlan = dailyRepo.findByUserIdAndPlanDate(userId, date)
                .orElseGet(() -> {
                    DailyMealPlan newPlan = new DailyMealPlan();
                    newPlan.setUserId(userId);
                    newPlan.setPlanDate(date);
                    newPlan.setTotalCalories(BigDecimal.ZERO);
                    newPlan.setStatus(PlanStatus.PLANNED);
                    newPlan.setCreatedAt(LocalDateTime.now());
                    return dailyRepo.save(newPlan);
                });

        // 2. Tạo MealItem mới
        MealItem item = new MealItem();
        item.setMealPlanId(dailyPlan.getMealPlanId());
        item.setCustomDishName(name);
        item.setCalories(cal);
        item.setProtein(pro);
        item.setCarbs(carb);
        item.setFat(fat);
        item.setMealTimeType(MealType.valueOf(mealTypeStr.toUpperCase()));
        item.setStatus("EATEN");
        item.setIsCustomEntry(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setQuantityMultiplier(BigDecimal.ONE);

        if (image != null && !image.isEmpty()) {
            String imgUrl = fileStorageService.storeFile(image);
            item.setImageUrl(imgUrl);
        } else {
            item.setImageUrl("/images/default-food.png");
        }

        itemRepo.save(item);
    }

    public MealItem getMealItemById(Integer itemId) {
        return itemRepo.findById(itemId).orElseThrow(() -> new RuntimeException("Not found"));
    }

    @Transactional
    public void deleteMealItem(Integer itemId) {
        itemRepo.deleteById(itemId);
    }
    
    @Transactional
    public void addRecipeToPlan(Integer userId, LocalDate date, Integer recipeId, String typeStr) {
        // 1. Tìm/Tạo Plan
        DailyMealPlan dailyPlan = dailyRepo.findByUserIdAndPlanDate(userId, date)
                .orElseGet(() -> {
                    DailyMealPlan newPlan = new DailyMealPlan();
                    newPlan.setUserId(userId);
                    newPlan.setPlanDate(date);
                    newPlan.setTotalCalories(BigDecimal.ZERO);
                    newPlan.setStatus(PlanStatus.PLANNED);
                    newPlan.setCreatedAt(LocalDateTime.now());
                    return dailyRepo.save(newPlan);
                });

        // 2. Lấy thông tin Recipe
        Recipe r = recipeRepo.findById(recipeId).orElseThrow(() -> new RuntimeException("Recipe not found"));
        // Tính toán lại để chắc chắn có calo
        recipeService.calculateRecipeMacros(r);

        // 3. Tạo MealItem
        MealItem item = new MealItem();
        item.setMealPlanId(dailyPlan.getMealPlanId());
        item.setRecipeId(recipeId);
        item.setCustomDishName(r.getName()); // Lưu tên để hiển thị nhanh
        item.setCalories(r.getTotalCalories());
        item.setProtein(r.getProtein());
        item.setCarbs(r.getCarbs());
        item.setFat(r.getFat());
        item.setImageUrl(r.getImageUrl());
        
        item.setMealTimeType(MealType.valueOf(typeStr.toUpperCase()));
        item.setStatus("PENDING");
        item.setQuantityMultiplier(BigDecimal.ONE);
        item.setIsCustomEntry(false); // Đây là món từ Recipe, không phải custom
        item.setCreatedAt(LocalDateTime.now());

        itemRepo.save(item);
    }
    
    public List<LocalDate> getUpcomingPlannedDates(Integer userId) {
    // Lấy các ngày có plan từ hôm nay trở đi
    return dailyRepo.findByUserIdAndPlanDateGreaterThanEqual(userId, LocalDate.now())
            .stream().map(DailyMealPlan::getPlanDate).collect(Collectors.toList());
}
}
