package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.UserRegisterDTO;
import com.group02.zaderfood.service.EmailService;
import com.group02.zaderfood.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // <--- Import này

import java.util.Random;

@Controller
@RequestMapping("/register")
public class RegisterController {

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    // --- INPUT EMAIL ---
    @GetMapping("/step1")
    public String showStep1() {
        return "auth/register-step1";
    }

    @PostMapping("/step1")
    public String processStep1(@RequestParam("email") String email,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (userService.isEmailExists(email)) {
            model.addAttribute("error", "This email is already in use!");
            return "auth/register-step1";
        }

        String otp = String.format("%06d", new Random().nextInt(999999));
        try {
            emailService.sendOtpEmail(email, otp);
        } catch (Exception e) {
            model.addAttribute("error", "Send email error: " + e.getMessage());
            return "auth/register-step1";
        }

        session.setAttribute("regEmail", email);
        session.setAttribute("regOtp", otp);

        redirectAttributes.addFlashAttribute("message", "A verification code has been sent to your email!");

        return "redirect:/register/step2";
    }

    // --- INPUT OTP ---
    @GetMapping("/step2")
    public String showStep2(HttpSession session) {
        if (session.getAttribute("regEmail") == null) {
            return "redirect:/register/step1";
        }
        return "auth/register-step2";
    }

    @PostMapping("/step2")
    public String processStep2(@RequestParam("otp") String otp,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        String sessionOtp = (String) session.getAttribute("regOtp");

        if (sessionOtp != null && sessionOtp.equals(otp)) {
            session.setAttribute("isVerified", true);
            redirectAttributes.addFlashAttribute("message", "Verification successful! Please set up your account.");
            return "redirect:/register/step3";
        }

        model.addAttribute("error", "OTP code is incorrect!");
        return "auth/register-step2";
    }

    // --- INPUT INFO ---
    @GetMapping("/step3")
    public String showStep3(HttpSession session, Model model) {
        if (session.getAttribute("isVerified") == null) {
            return "redirect:/register/step1";
        }
        model.addAttribute("user", new UserRegisterDTO());
        return "auth/register-step3";
    }

    @PostMapping("/step3")
    public String processStep3(@ModelAttribute("user") UserRegisterDTO dto,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        String email = (String) session.getAttribute("regEmail");
        dto.setEmail(email);

        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            model.addAttribute("error", "Confirmation password does not match!");
            return "auth/register-step3";
        }

        try {
            userService.registerUser(dto);

            session.removeAttribute("regEmail");
            session.removeAttribute("regOtp");
            session.removeAttribute("isVerified");

            redirectAttributes.addFlashAttribute("message", "Registration successful! Please log in.");

            return "redirect:/login";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "auth/register-step3";
        }
    }
    
    // --- RESEND OTP ---
    @GetMapping("/resend")
    public String resendOtp(HttpSession session, RedirectAttributes redirectAttributes) {
        String email = (String) session.getAttribute("regEmail");
        
        if (email == null) {
            return "redirect:/register/step1";
        }
        
        String newOtp = String.format("%06d", new Random().nextInt(999999));

        try {
            emailService.sendOtpEmail(email, newOtp);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Send email error: " + e.getMessage());
            return "redirect:/register/step2";
        }

        session.setAttribute("regOtp", newOtp);

        redirectAttributes.addFlashAttribute("message", "New verification code has been sent!");
        
        return "redirect:/register/step2";
    }
}
