package com.group02.zaderfood.controller; // Lưu ý package có .api hay không tùy cấu trúc folder của bạn

import com.group02.zaderfood.dto.AiFoodResponse;
import com.group02.zaderfood.entity.MealItem;
import com.group02.zaderfood.service.AiFoodService;
import com.group02.zaderfood.service.CustomUserDetails;
import com.group02.zaderfood.service.EmailService;
import com.group02.zaderfood.service.MealPlanService;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource; // Class cha của InputStreamResource
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/meal-plan")
public class MealApiController {

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AiFoodService aiFoodService;

    // 1. Cập nhật trạng thái món ăn (Mark as Eaten / Undo)
    @PostMapping("/item/{itemId}/status")
    public ResponseEntity<?> updateItemStatus(@PathVariable Integer itemId, @RequestParam String status) {
        try {
            mealPlanService.updateMealItemStatus(itemId, status);
            return ResponseEntity.ok(Map.of("message", "Status updated", "newStatus", status));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // 2. Tạo Shopping List từ ngày cụ thể
    @PostMapping("/shopping-list/create")
    public ResponseEntity<?> createShoppingList(@RequestParam String dateStr, @RequestParam Integer userId) {
        try {
            LocalDate date = LocalDate.parse(dateStr);
            mealPlanService.createShoppingListForDate(userId, date);
            return ResponseEntity.ok(Map.of("message", "Shopping list created successfully!"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create list: " + e.getMessage()));
        }
    }

    // 3. Tìm và thêm Snack (Gợi ý ăn nhẹ)
    @PostMapping("/day/add-snack")
    public ResponseEntity<?> addSnackSuggestion(@RequestParam String dateStr, @RequestParam Integer userId) {
        try {
            LocalDate date = LocalDate.parse(dateStr);
            boolean success = mealPlanService.addSmartSnack(userId, date);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "Snack added to your plan!"));
            } else {
                return ResponseEntity.ok(Map.of("message", "No suitable snacks found or plan full."));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/shopping-list/export-excel")
    public ResponseEntity<Resource> exportExcel(
            @RequestParam String dateStr,
            @AuthenticationPrincipal CustomUserDetails user) { // [FIX] Lấy user từ Session

        if (user == null) {
            return ResponseEntity.status(401).build(); // Chưa đăng nhập -> 401 Unauthorized
        }

        try {
            LocalDate date = LocalDate.parse(dateStr);
            String filename = "ShoppingList_" + dateStr + ".xlsx";

            // Lấy ID thật từ user đang đăng nhập
            ByteArrayInputStream in = mealPlanService.exportShoppingListToExcel(user.getUserId(), date);

            InputStreamResource resource = new InputStreamResource(in);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                    .body(resource);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // API 5: Gửi Email
    @PostMapping("/shopping-list/send-email")
    public ResponseEntity<?> sendEmail(
            @RequestParam String dateStr,
            @AuthenticationPrincipal CustomUserDetails user) {

        System.err.println("----------- handlder ---------------");

        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Please login first"));
        }

        try {
            LocalDate date = LocalDate.parse(dateStr);
            emailService.sendShoppingListEmail(user.getUserId(), date);
            return ResponseEntity.ok(Map.of("message", "Email sent successfully!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed: " + e.getMessage()));
        }
    }

    @PostMapping("/item/{itemId}/update")
    public ResponseEntity<?> updateMealItem(
            @PathVariable Integer itemId,
            @RequestParam("dishName") String dishName,
            @RequestParam("calories") BigDecimal calories,
            @RequestParam(value = "protein", defaultValue = "0") BigDecimal protein,
            @RequestParam(value = "carbs", defaultValue = "0") BigDecimal carbs,
            @RequestParam(value = "fat", defaultValue = "0") BigDecimal fat,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        try {
            mealPlanService.updateMealItemDetails(itemId, dishName, calories, protein, carbs, fat, image);
            return ResponseEntity.ok(Map.of("message", "Updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/analyze-food")
    public ResponseEntity<?> analyzeFood(
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "description", required = false) String description) {

        try {
            // Validate: Phải có ít nhất 1 trong 2
            if ((image == null || image.isEmpty()) && (description == null || description.trim().isEmpty())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Please provide an image or description."));
            }

            // Gọi AI Service
            AiFoodResponse response = aiFoodService.analyzeFood(description, image);

            // Kiểm tra lỗi từ AI Service
            if (response.getError() != null) {
                return ResponseEntity.badRequest().body(Map.of("error", response.getError()));
            }

            // Trả về kết quả JSON
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", "AI Service Error: " + e.getMessage()));
        }
    }

    // API 7: Thêm món ăn mới
    @PostMapping("/item/add")
    public ResponseEntity<?> addMealItem(
            @RequestParam("dateStr") String dateStr,
            @RequestParam("type") String type,
            @RequestParam("dishName") String dishName,
            @RequestParam("calories") BigDecimal calories,
            @RequestParam(value = "protein", defaultValue = "0") BigDecimal protein,
            @RequestParam(value = "carbs", defaultValue = "0") BigDecimal carbs,
            @RequestParam(value = "fat", defaultValue = "0") BigDecimal fat,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @AuthenticationPrincipal CustomUserDetails user) {
        
        try {
            if (user == null) return ResponseEntity.status(401).build();
            LocalDate date = LocalDate.parse(dateStr);
            
            System.err.println(calories);
            
            mealPlanService.addCustomMealItem(user.getUserId(), date, type, dishName, calories, protein, carbs, fat, image);
            return ResponseEntity.ok(Map.of("message", "New meal added!"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    
    // API 8: Lấy chi tiết món ăn (Để fill vào form Edit)
    @GetMapping("/item/{itemId}")
    public ResponseEntity<?> getMealItemDetail(@PathVariable Integer itemId) {
        try {
            MealItem item = mealPlanService.getMealItemById(itemId);
            return ResponseEntity.ok(item);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // API 9: Xóa món ăn (DELETE)
    @DeleteMapping("/item/{itemId}")
    public ResponseEntity<?> deleteMealItem(@PathVariable Integer itemId) {
        try {
            mealPlanService.deleteMealItem(itemId);
            return ResponseEntity.ok(Map.of("message", "Deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/add-recipe-to-plan")
    public ResponseEntity<?> addRecipeToPlan(
            @RequestParam String dateStr,
            @RequestParam Integer recipeId,
            @RequestParam String mealType, // "SNACK"
            @AuthenticationPrincipal CustomUserDetails user) {
        
        try {
            if (user == null) return ResponseEntity.status(401).build();
            
            mealPlanService.addRecipeToPlan(user.getUserId(), LocalDate.parse(dateStr), recipeId, mealType);
            return ResponseEntity.ok(Map.of("message", "Added successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
