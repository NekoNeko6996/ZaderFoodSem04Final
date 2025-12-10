package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.IngredientInputDTO;
import com.group02.zaderfood.entity.Ingredient;
import com.group02.zaderfood.entity.User;
import com.group02.zaderfood.repository.UserRepository;
import com.group02.zaderfood.service.AdminIngredientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/admin")
public class AdminIngredientController {

    @Autowired
    private AdminIngredientService adminService;
    @Autowired
    private UserRepository userRepository;

    // --- 1. TRANG QUẢN LÝ NGUYÊN LIỆU ---
    @GetMapping("/ingredients")
    public String listIngredients(Model model,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Boolean status,
            @RequestParam(defaultValue = "1") int page) {

        Page<Ingredient> pageData = adminService.getIngredients(keyword, categoryId, status, page, 10);

        model.addAttribute("ingredients", pageData.getContent());
        model.addAttribute("categories", adminService.getAllCategories()); // Cho dropdown
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageData.getTotalPages());

        // Giữ lại giá trị filter trên form
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("status", status);

        // DTO cho form thêm mới
        model.addAttribute("newIngredient", new IngredientInputDTO());

        return "admin/ingredients";
    }

    @PostMapping("/ingredients/add")
    public String addIngredient(@ModelAttribute IngredientInputDTO dto, Principal principal, RedirectAttributes ra) {
        try {
            String email = principal.getName();
            User admin = userRepository.findByEmail(email).orElseThrow();
            adminService.createIngredient(dto, admin.getUserId());
            ra.addFlashAttribute("message", "Added new ingredient successfully!");
            ra.addFlashAttribute("messageType", "success");
        } catch (Exception e) {
            ra.addFlashAttribute("message", "Error: " + e.getMessage());
            ra.addFlashAttribute("messageType", "error");
        }
        return "redirect:/admin/ingredients";
    }

    @PostMapping("/ingredients/toggle/{id}")
    public String toggleIngredient(@PathVariable Integer id, RedirectAttributes ra) {
        adminService.toggleStatus(id);
        ra.addFlashAttribute("message", "Updated ingredient status!");
        ra.addFlashAttribute("messageType", "success");
        return "redirect:/admin/ingredients";
    }

    @PostMapping("/ingredients/delete/{id}")
    public String deleteIngredient(@PathVariable Integer id, RedirectAttributes ra) {
        adminService.deleteIngredient(id);
        ra.addFlashAttribute("message", "Ingredient deleted.");
        ra.addFlashAttribute("messageType", "success");
        return "redirect:/admin/ingredients";
    }

    // --- 2. TRANG QUẢN LÝ DANH MỤC ---
    @GetMapping("/categories")
    public String listCategories(Model model) {
        model.addAttribute("categories", adminService.getAllCategories());
        return "admin/categories";
    }

    @PostMapping("/categories/add")
    public String addCategory(@RequestParam String name, RedirectAttributes ra) {
        try {
            adminService.createCategory(name);
            ra.addFlashAttribute("message", "Category added!");
            ra.addFlashAttribute("messageType", "success");
        } catch (Exception e) {
            ra.addFlashAttribute("messageType", "error");
        }
        return "redirect:/admin/categories";
    }

    // Trong AdminIngredientController.java
    @PostMapping("/categories/delete/{id}")
    public String deleteCategory(@PathVariable Integer id, RedirectAttributes ra) {
        try {
            // Gọi hàm xóa thông minh mới
            String result = adminService.deleteCategorySmart(id);

            if ("HARD".equals(result)) {
                ra.addFlashAttribute("message", "Category deleted permanently!");
                ra.addFlashAttribute("messageType", "success");
            } else {
                ra.addFlashAttribute("message", "Category is in use. Switched to Soft Delete (Archived).");
                ra.addFlashAttribute("messageType", "warning"); // Màu vàng cảnh báo
            }

        } catch (Exception e) {
            ra.addFlashAttribute("message", "Error deleting category: " + e.getMessage());
            ra.addFlashAttribute("messageType", "error");
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/update")
    public String updateCategory(@RequestParam Integer id, @RequestParam String name, RedirectAttributes ra) {
        try {
            adminService.updateCategory(id, name);
            ra.addFlashAttribute("message", "Category updated successfully!");
            ra.addFlashAttribute("messageType", "success");
        } catch (Exception e) {
            ra.addFlashAttribute("message", "Error updating category: " + e.getMessage());
            ra.addFlashAttribute("messageType", "error");
        }
        return "redirect:/admin/categories";
    }
}
