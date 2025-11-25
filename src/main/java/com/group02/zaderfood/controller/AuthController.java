package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.UserRegisterDTO;
import com.group02.zaderfood.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    // Hiển thị form Login
    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }

    // Hiển thị form Register
    @GetMapping("/register")
    public String showRegisterPage(Model model) {
        model.addAttribute("user", new UserRegisterDTO()); // Tạo object rỗng để hứng dữ liệu
        return "register";
    }

    // Xử lý submit form Register
    @PostMapping("/register")
    public String processRegister(@ModelAttribute("user") UserRegisterDTO registerDTO, Model model) {
        try {
            userService.registerUser(registerDTO);
            // Đăng ký thành công -> Chuyển về login với thông báo
            return "redirect:/login?registerSuccess"; 
        } catch (Exception e) {
            // Đăng ký thất bại -> Ở lại trang register và hiện lỗi
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }
}