package com.group02.zaderfood.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String showLoginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            @RequestParam(value = "message", required = false) String messageParam,
            Model model,
            HttpServletRequest request) {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }

        if (logout != null) {
            model.addAttribute("message", "You have successfully logged out.");
        }

        if (messageParam != null) {
            if (messageParam.equals("reset_success")) {
                model.addAttribute("message", "Password reset successful! Please log in.");
            } else if (messageParam.equals("register_success")) {
                 model.addAttribute("message", "Registration successful! Please log in.");
            }
        }

        if (error != null) {
            HttpSession session = request.getSession(false);
            String errorMessage = "Email or password is incorrect.";
            
            if (session != null) {
                Exception ex = (Exception) session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
                if (ex != null) {
                    if (ex.getMessage().contains("User is disabled")) {
                        errorMessage = "Account not activated. Please check your email.";
                    } else if (ex.getMessage().contains("Bad credentials")) {
                        errorMessage = "Email or password is incorrect.";
                    } else {
                        errorMessage = ex.getMessage();
                    }
                }
            }
            model.addAttribute("error", errorMessage);
        }

        return "login";
    }
}