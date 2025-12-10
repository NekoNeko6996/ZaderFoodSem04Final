package com.group02.zaderfood.controller;

import com.group02.zaderfood.entity.Recipe;
import com.group02.zaderfood.service.AdminRecipeService;
import com.group02.zaderfood.service.RecipeService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller // QUAN TRỌNG: Dùng @Controller để trả về View (HTML), KHÔNG dùng @RestController
@RequestMapping("/admin/recipes") // URL gốc cho trang quản trị
public class AdminRecipeController {

    @Autowired
    private AdminRecipeService adminRecipeService;

    @Autowired
    private RecipeService recipeService;

    // 1. GET: Hiển thị trang danh sách chờ duyệt
    // Trả về file: templates/admin/recipe-pending-list.html
    @GetMapping("/pending")
    public String viewPendingRecipes(Model model) {
        // Lấy dữ liệu từ Service và đẩy vào Model với tên biến là "recipes"
        model.addAttribute("recipes", adminRecipeService.getPendingRecipes());
        return "admin/recipe-pending-list";
    }

    // 2. GET: Hiển thị trang chi tiết để duyệt/sửa
    // Trả về file: templates/admin/recipe-detail.html
    @GetMapping("/{id}")
    public String viewRecipeDetail(@PathVariable Integer id, Model model) {
        Recipe recipe = adminRecipeService.getRecipeDetail(id);
        recipeService.calculateRecipeMacros(recipe);
        model.addAttribute("recipe", recipe); // Đẩy đối tượng "recipe" sang View để hiển thị form
        return "admin/recipe-detail";
    }

    // 3. POST: Xử lý cập nhật thông tin (Khi bấm nút Lưu)
    // Dùng @ModelAttribute để hứng dữ liệu từ Form HTML gửi lên
    @PostMapping("/{id}/update")
    public String updateRecipe(@PathVariable Integer id, @ModelAttribute Recipe recipe, RedirectAttributes ra) {
        adminRecipeService.updateRecipeContent(id, recipe);

        ra.addFlashAttribute("message", "Recipe updated successfully!");
        ra.addFlashAttribute("messageType", "success");

        return "redirect:/admin/recipes/" + id;
    }

    // 4. POST: Duyệt công thức
    @PostMapping("/{id}/approve")
    public String approveRecipe(@PathVariable Integer id, RedirectAttributes ra) {
        adminRecipeService.approveRecipe(id);

        // Thêm thông báo
        ra.addFlashAttribute("message", "Recipe has been approved and published!");
        ra.addFlashAttribute("messageType", "success");

        return "redirect:/admin/recipes/pending";
    }

    // 5. POST: Từ chối công thức
    @PostMapping("/{id}/reject")
    public String rejectRecipe(@PathVariable Integer id, RedirectAttributes ra) {
        adminRecipeService.rejectRecipe(id);

        // Thêm thông báo
        ra.addFlashAttribute("message", "Recipe has been rejected and removed.");
        ra.addFlashAttribute("messageType", "success"); // Hoặc dùng 'error' nếu muốn hiện màu đỏ

        return "redirect:/admin/recipes/pending";
    }

    // 6
    @PostMapping("/api/steps/update")
    @ResponseBody // Quan trọng: Trả về JSON, không phải HTML view
    public ResponseEntity<?> updateSingleStep(@RequestParam Integer stepId,
            @RequestParam String instruction) {
        try {
            adminRecipeService.updateStepInstruction(stepId, instruction);
            return ResponseEntity.ok(Map.of("success", true, "message", "Step updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/api/update-general")
    @ResponseBody // Trả về JSON
    public ResponseEntity<?> updateRecipeGeneralApi(@PathVariable Integer id, @ModelAttribute Recipe recipe) {
        try {
            // Sử dụng lại service cũ để update nội dung
            adminRecipeService.updateRecipeContent(id, recipe);
            return ResponseEntity.ok(Map.of("success", true, "message", "Recipe information saved successfully!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }
}
