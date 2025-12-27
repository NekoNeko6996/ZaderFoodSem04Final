package com.group02.zaderfood.service;

import com.group02.zaderfood.dto.ShoppingListItemDTO;
import com.group02.zaderfood.entity.*;
import com.group02.zaderfood.repository.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Service
public class ShoppingListService {

    @Autowired
    private ShoppingListRepository listRepo;
    @Autowired
    private ShoppingListItemRepository itemRepo;
    @Autowired
    private DailyMealPlanRepository mealPlanRepo;
    @Autowired
    private MealItemRepository mealItemRepo;
    @Autowired
    private RecipeIngredientRepository recipeIngredientRepo;
    @Autowired
    private IngredientRepository ingredientRepo;
    @Autowired
    private IngredientCategoryRepository categoryRepo;
    @Autowired
    private EmailService emailService;
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private ShoppingListItemRepository shoppingItemRepo;
    @Autowired
    private ShoppingListRepository shoppingListRepo;
    @Autowired
    private UserPantryRepository pantryRepo;

    @Transactional
    public ShoppingList generateOrGetList(Integer userId, LocalDate fromDate, LocalDate toDate) {
        // 1. Kiểm tra xem đã có list cho khoảng này chưa
        Optional<ShoppingList> existingList = listRepo.findByUserIdAndFromDateAndToDate(userId, fromDate, toDate);
        if (existingList.isPresent()) {
            return existingList.get();
        }

        // 2. Nếu chưa có, tạo mới
        ShoppingList newList = new ShoppingList();
        newList.setUserId(userId);
        newList.setName("Shopping List (" + fromDate + " to " + toDate + ")");
        newList.setFromDate(fromDate);
        newList.setToDate(toDate);
        newList.setStatus("PENDING");
        newList.setCreatedAt(LocalDateTime.now());
        ShoppingList savedList = listRepo.save(newList);

        // 3. Tính toán nguyên liệu
        calculateAndSaveItems(userId, savedList);

        return savedList;
    }

    private void calculateAndSaveItems(Integer userId, ShoppingList list) {
        // Lấy tất cả Meal Plan trong khoảng thời gian
        List<DailyMealPlan> plans = mealPlanRepo.findByUserIdAndPlanDateBetween(userId, list.getFromDate(), list.getToDate());
        List<Integer> planIds = plans.stream().map(DailyMealPlan::getMealPlanId).collect(Collectors.toList());

        if (planIds.isEmpty()) {
            return;
        }

        // Lấy tất cả Meal Item
        List<MealItem> mealItems = mealItemRepo.findByMealPlanIdIn(planIds);

        // Map để gộp nguyên liệu: Key = IngredientId, Value = Quantity
        Map<Integer, BigDecimal> ingredientTotals = new HashMap<>();
        Map<Integer, String> ingredientUnits = new HashMap<>(); // Lưu unit tạm

        // List các món Custom (không có RecipeId)
        List<String> customItems = new ArrayList<>();

        for (MealItem meal : mealItems) {
            if (meal.getRecipeId() != null) {
                // Lấy nguyên liệu của Recipe
                List<RecipeIngredient> recipeIngs = recipeIngredientRepo.findByRecipeRecipeId(meal.getRecipeId());

                // Hệ số nhân (VD: Ăn 2 suất -> nhân đôi)
                BigDecimal multiplier = meal.getQuantityMultiplier() != null ? meal.getQuantityMultiplier() : BigDecimal.ONE;

                for (RecipeIngredient ri : recipeIngs) {
                    BigDecimal totalQty = ri.getQuantity().multiply(multiplier);

                    ingredientTotals.merge(ri.getIngredientId(), totalQty, BigDecimal::add);

                    // Lưu unit (giả sử cùng 1 nguyên liệu thì cùng unit, nếu khác cần logic quy đổi phức tạp hơn)
                    if (!ingredientUnits.containsKey(ri.getIngredientId())) {
                        ingredientUnits.put(ri.getIngredientId(), ri.getUnit());
                    }
                }
            } else if (meal.getIsCustomEntry() != null && meal.getIsCustomEntry()) {
                // Món tự nhập -> Thêm tên món vào list
                customItems.add(meal.getCustomDishName());
            }
        }

        // 4. Lưu vào DB
        List<ShoppingListItem> listItems = new ArrayList<>();

        for (Map.Entry<Integer, BigDecimal> entry : ingredientTotals.entrySet()) {
            Integer ingredientId = entry.getKey();
            BigDecimal neededQty = entry.getValue(); // VD: Cần 300g
            String unit = ingredientUnits.get(ingredientId);

            ShoppingListItem item = new ShoppingListItem();
            item.setListId(list.getListId());
            item.setIngredientId(ingredientId);
            item.setUnit(unit);

            // --- [LOGIC MỚI: TRỪ ĐI LƯỢNG ĐANG CÓ TRONG PANTRY] ---
            // Tìm trong Pantry xem có không
            UserPantry inPantry = pantryRepo.findByUserIdAndIngredientId(userId, ingredientId);

            BigDecimal finalQuantityToBuy = neededQty;
            boolean autoCheck = false;

            if (inPantry != null && inPantry.getQuantity() != null) {
                // Kiểm tra đơn vị có khớp không (VD: cùng là 'g' hoặc 'ml')
                // (Lưu ý: Nếu khác đơn vị như 'g' vs 'kg' thì cần logic quy đổi phức tạp hơn, tạm thời so sánh chuỗi)
                if (unit.equalsIgnoreCase(inPantry.getUnit())) {

                    BigDecimal inStock = inPantry.getQuantity(); // VD: Có 150g

                    if (inStock.compareTo(neededQty) >= 0) {
                        // Trường hợp 1: Tủ có ĐỦ hoặc DƯ (Có 400g >= Cần 300g)
                        finalQuantityToBuy = BigDecimal.ZERO;
                        autoCheck = true; // Tự động gạch đi
                    } else {
                        // Trường hợp 2: Tủ có MỘT ÍT (Có 150g < Cần 300g)
                        // Cần mua = 300 - 150 = 150g
                        finalQuantityToBuy = neededQty.subtract(inStock);
                        autoCheck = false; // Vẫn phải đi mua phần thiếu
                    }
                }
            }

            // Lưu số lượng thực tế cần mua
            // Nếu finalQuantityToBuy là 0 (đã đủ), ta vẫn lưu item nhưng set IsBought=true để user biết là không cần mua
            item.setQuantity(finalQuantityToBuy);
            item.setIsBought(autoCheck);

            // Chỉ thêm vào list nếu cần mua > 0 HOẶC user muốn thấy cả những món đã đủ (tùy chọn)
            // Ở đây mình thêm tất cả để user review được tổng thể
            listItems.add(item);
        }

        // Lưu món Custom
        for (String customName : customItems) {
            ShoppingListItem item = new ShoppingListItem();
            item.setListId(list.getListId());
            item.setCustomItemName(customName); // Tên món custom
            item.setQuantity(BigDecimal.ONE);
            item.setUnit("portion");
            item.setIsBought(false);
            listItems.add(item);
        }

        itemRepo.saveAll(listItems);
    }

    // Lấy chi tiết list và nhóm theo Category
    public Map<String, List<ShoppingListItemDTO>> getListDetailsGrouped(Integer listId) {
        List<ShoppingListItem> items = itemRepo.findByListId(listId);
        
        ShoppingList list = listRepo.findById(listId).orElseThrow();
        Integer userId = list.getUserId();
        List<UserPantry> userPantry = pantryRepo.findByUserIdOrderByExpiryDateAsc(userId);
        
        Map<Integer, BigDecimal> pantryMap = new HashMap<>();
        for (UserPantry p : userPantry) {
            pantryMap.merge(p.getIngredientId(), p.getQuantity(), BigDecimal::add);
        }

        // DTO để chứa thông tin hiển thị (Tên, Ảnh, Category...)
        List<ShoppingListItemDTO> dtos = items.stream().map(item -> {
            ShoppingListItemDTO dto = new ShoppingListItemDTO();
            dto.setItemId(item.getItemId());
            dto.setQuantity(item.getQuantity());
            dto.setUnit(item.getUnit());
            dto.setIsBought(item.getIsBought());

            if (item.getIngredientId() != null) {
                Ingredient ing = ingredientRepo.findById(item.getIngredientId()).orElse(null);
                if (ing != null) {
                    dto.setName(ing.getName());
                    dto.setImageUrl(ing.getImageUrl());

                    if (ing.getCategoryId() != null) {
                        IngredientCategory cat = categoryRepo.findById(ing.getCategoryId()).orElse(null);
                        dto.setCategoryName(cat != null ? cat.getName() : "Other");
                    } else {
                        dto.setCategoryName("Other");
                    }
                    
                    BigDecimal stock = pantryMap.getOrDefault(item.getIngredientId(), BigDecimal.ZERO);
                    dto.setPantryStock(stock);
                }
            } else {
                dto.setName(item.getCustomItemName());
                dto.setCategoryName("Custom / Other");
                dto.setImageUrl(null);
                dto.setPantryStock(BigDecimal.ZERO);
            }
            return dto;
        }).collect(Collectors.toList());

        // Group by Category Name
        return dtos.stream().collect(Collectors.groupingBy(ShoppingListItemDTO::getCategoryName));
    }

    @Transactional
    public void toggleItemStatus(Integer itemId) {
        ShoppingListItem item = shoppingItemRepo.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));

        // Đảo ngược trạng thái
        boolean newStatus = !Boolean.TRUE.equals(item.getIsBought());
        item.setIsBought(newStatus);
        shoppingItemRepo.save(item);

        if (newStatus) {
            addToPantry(item);
        }
    }

    private void addToPantry(ShoppingListItem item) {
        // 1. Chỉ thêm những món có liên kết với Ingredient (Món custom không có ID thì không thêm được hoặc cần logic riêng)
        if (item.getIngredientId() == null) {
            return;
        }

        // 2. Lấy thông tin User sở hữu Shopping List
        ShoppingList listHeader = shoppingListRepo.findById(item.getListId())
                .orElseThrow(() -> new RuntimeException("List not found"));
        Integer userId = listHeader.getUserId();

        // 3. Kiểm tra xem nguyên liệu này đã có trong tủ chưa
        UserPantry pantryItem = pantryRepo.findByUserIdAndIngredientId(userId, item.getIngredientId());

        if (pantryItem != null) {
            // TRƯỜNG HỢP A: Đã có -> Cộng dồn số lượng
            // (Lưu ý: Cần xử lý quy đổi đơn vị nếu khác nhau, ở đây giả sử cùng đơn vị cho đơn giản)
            if (pantryItem.getUnit().equalsIgnoreCase(item.getUnit())) {
                pantryItem.setQuantity(pantryItem.getQuantity().add(item.getQuantity()));
                pantryItem.setCreatedAt(LocalDateTime.now()); // Update thời gian mới nhất
                pantryRepo.save(pantryItem);
            } else {
                // Khác đơn vị -> Tạo dòng mới (hoặc bạn phải viết hàm convertUnit)
                createNewPantryItem(userId, item);
            }
        } else {
            // TRƯỜNG HỢP B: Chưa có -> Tạo mới
            createNewPantryItem(userId, item);
        }
    }

    private void createNewPantryItem(Integer userId, ShoppingListItem item) {
        UserPantry newPantry = new UserPantry();
        newPantry.setUserId(userId);
        newPantry.setIngredientId(item.getIngredientId());
        newPantry.setQuantity(item.getQuantity());
        newPantry.setUnit(item.getUnit());
        newPantry.setExpiryDate(null); // [YC] Để null, người dùng nhập sau
        newPantry.setCreatedAt(LocalDateTime.now());

        pantryRepo.save(newPantry);
    }

    public ShoppingList getListById(Integer listId) {
        return listRepo.findById(listId).orElse(null);
    }

    // Logic Xuất Excel
    public ByteArrayInputStream exportToExcel(Integer listId) throws IOException {
        ShoppingList list = getListById(listId);
        Map<String, List<ShoppingListItemDTO>> grouped = getListDetailsGrouped(listId);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Shopping List");

            // Style Header
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            int rowIdx = 0;

            // Title
            Row titleRow = sheet.createRow(rowIdx++);
            titleRow.createCell(0).setCellValue(list.getName());

            rowIdx++; // Dòng trống

            // Loop qua từng Category
            for (Map.Entry<String, List<ShoppingListItemDTO>> entry : grouped.entrySet()) {
                // Tên Category
                Row catRow = sheet.createRow(rowIdx++);
                Cell catCell = catRow.createCell(0);
                catCell.setCellValue(entry.getKey().toUpperCase());
                catCell.setCellStyle(headerStyle);

                // Header cột
                Row headerRow = sheet.createRow(rowIdx++);
                headerRow.createCell(0).setCellValue("Item Name");
                headerRow.createCell(1).setCellValue("Quantity");
                headerRow.createCell(2).setCellValue("Unit");
                headerRow.createCell(3).setCellValue("Status");

                // Data
                for (ShoppingListItemDTO item : entry.getValue()) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(item.getName());
                    row.createCell(1).setCellValue(item.getQuantity().doubleValue());
                    row.createCell(2).setCellValue(item.getUnit());
                    row.createCell(3).setCellValue(item.getIsBought() ? "Bought" : "Pending");
                }
                rowIdx++; // Dòng trống giữa các nhóm
            }

            sheet.autoSizeColumn(0);
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    public void sendListViaEmail(Integer userId, Integer listId) throws Exception { // Thêm throws Exception
        // A. Lấy dữ liệu Shopping List
        ShoppingList list = getListById(listId);
        if (list == null) {
            throw new Exception("List not found");
        }

        // B. Lấy các món ăn (đã nhóm theo category)
        Map<String, List<ShoppingListItemDTO>> groupedItems = getListDetailsGrouped(listId);

        // C. Lấy thông tin User (để lấy Email)
        com.group02.zaderfood.entity.User user = userRepo.findById(userId)
                .orElseThrow(() -> new Exception("User not found"));

        // D. Gọi hàm gửi mail MỚI trong EmailService
        emailService.sendRangeShoppingListEmail(user, list, groupedItems);
    }
}
