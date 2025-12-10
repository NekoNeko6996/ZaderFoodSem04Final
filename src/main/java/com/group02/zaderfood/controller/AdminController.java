package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.AdminDashboardDTO;
import com.group02.zaderfood.service.AdminService;
import com.group02.zaderfood.service.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @GetMapping("/dashboard")
    public String dashboard(Model model, @AuthenticationPrincipal CustomUserDetails currentUser) {
        // Lấy dữ liệu thống kê
        AdminDashboardDTO stats = adminService.getDashboardStats();
        
        // Đẩy sang View
        model.addAttribute("stats", stats);
        
        // Trả về file admin_dashboard.html (đặt trong folder templates/admin/)
        return "admin/admin_dashboard";
    }
}