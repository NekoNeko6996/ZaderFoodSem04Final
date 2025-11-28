package com.group02.zaderfood.controller;

import com.group02.zaderfood.entity.User;
import com.group02.zaderfood.entity.enums.UserRole;
import com.group02.zaderfood.entity.enums.UserStatus;
import com.group02.zaderfood.repository.UserRepository;
import com.group02.zaderfood.service.AdminUserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.security.Principal;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public String listUsers(Model model,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "1") int page,
            Principal principal) { // 1. Inject Principal vào đây

        // 2. Lấy User ID của người đang đăng nhập
        String currentEmail = principal.getName();
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Current user not found"));

        int pageSize = 5;

        // 3. Truyền ID vào service để loại trừ
        Page<User> userPage = adminUserService.getUsers(currentUser.getUserId(), keyword, role, status, page, pageSize);

        // ... các đoạn code addAttribute phía sau giữ nguyên
        model.addAttribute("users", userPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", userPage.getTotalPages());
        model.addAttribute("totalItems", userPage.getTotalElements());
        model.addAttribute("keyword", keyword);
        model.addAttribute("role", role);
        model.addAttribute("status", status);

        model.addAttribute("roles", UserRole.values());
        model.addAttribute("statuses", UserStatus.values());
        model.addAttribute("newUser", new User());

        return "admin/users";
    }

    @PostMapping("/add")
    public String addUser(@ModelAttribute User newUser,
            @RequestParam("rawPassword") String rawPassword, // Hứng password từ form
            RedirectAttributes redirectAttributes) {
        try {
            // Truyền rawPassword vào service
            adminUserService.createUser(newUser, rawPassword);

            redirectAttributes.addFlashAttribute("message", "User created successfully!");
            redirectAttributes.addFlashAttribute("messageType", "success");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Error creating user: " + e.getMessage());
            redirectAttributes.addFlashAttribute("messageType", "error");
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/toggle-status/{id}")
    public String toggleStatus(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            adminUserService.toggleUserStatus(id);
            redirectAttributes.addFlashAttribute("message", "User status updated successfully!");
            redirectAttributes.addFlashAttribute("messageType", "success");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Error updating status: " + e.getMessage());
            redirectAttributes.addFlashAttribute("messageType", "error");
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/export")
    public void exportToExcel(HttpServletResponse response) throws IOException {
        adminUserService.exportUsersToExcel(response);
    }

    @PostMapping("/ban/{id}")
    public String banUser(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            adminUserService.banUser(id);
            redirectAttributes.addFlashAttribute("message", "User has been banned permanently!");
            redirectAttributes.addFlashAttribute("messageType", "success");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Could not ban user: " + e.getMessage());
            redirectAttributes.addFlashAttribute("messageType", "error");
        }
        return "redirect:/admin/users";
    }
}
