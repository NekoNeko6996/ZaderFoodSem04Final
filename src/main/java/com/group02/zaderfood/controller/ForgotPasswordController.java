package com.group02.zaderfood.controller; // Đổi package theo project của bạn

import com.group02.zaderfood.entity.User;
import com.group02.zaderfood.repository.UserRepository;
import com.group02.zaderfood.service.EmailService; 
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Random;

@Controller
public class ForgotPasswordController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // --- INPUT EMAIL ---
    @GetMapping("/forgot-password")
    public String showEmailForm() {
        return "auth/forgot-password-email"; 
    }

    @PostMapping("/forgot-password/send-otp")
    public String processEmail(@RequestParam("email") String email, 
                               HttpSession session, 
                               Model model) {
        
        User user = userRepository.findByEmail(email).orElse(null);
        
        if (user == null) {
            model.addAttribute("error", "Email does not exist in the system!");
            return "auth/forgot-password-email";
        }

        // create OTP
        String otp = String.format("%06d", new Random().nextInt(999999));

        // send mail
        try {
            emailService.sendOtpEmail(email, otp);
        } catch (Exception e) {
            model.addAttribute("error", "Send email error: " + e.getMessage());
            return "auth/forgot-password-email";
        }

        // save to Session
        session.setAttribute("otp", otp);
        session.setAttribute("email", email);
        session.setAttribute("otpTime", System.currentTimeMillis());

        return "redirect:/forgot-password/verify-otp";
    }

    // --- INPUT OTP ---
    @GetMapping("/forgot-password/verify-otp")
    public String showOtpForm() {
        return "auth/forgot-password-otp";
    }

    @PostMapping("/forgot-password/verify-otp")
    public String verifyOtp(@RequestParam("otp") String otpInput, 
                            HttpSession session, 
                            Model model) {
        
        String otpSession = (String) session.getAttribute("otp");
        Long otpTime = (Long) session.getAttribute("otpTime");

        if (otpSession == null) {
            model.addAttribute("error", "Session expired, please try again.");
            return "auth/forgot-password-otp";
        }

        if (!otpSession.equals(otpInput)) {
            model.addAttribute("error", "OTP code is incorrect!");
            return "auth/forgot-password-otp";
        }
        
        // Check expires in 5 minutes
        if (System.currentTimeMillis() - otpTime > 5 * 60 * 1000) {
             model.addAttribute("error", "OTP code has expired!");
             return "auth/forgot-password-otp";
        }

        return "redirect:/forgot-password/change-password";
    }

    // --- CHANGE PASSWORD ---
    @GetMapping("/forgot-password/change-password")
    public String showChangePasswordForm(HttpSession session) {
        if (session.getAttribute("email") == null) {
            return "redirect:/forgot-password";
        }
        return "auth/forgot-password-change";
    }

    @PostMapping("/forgot-password/change-password")
    public String processChangePassword(@RequestParam("password") String newPassword,
                                        @RequestParam("confirmPassword") String confirmPassword,
                                        HttpSession session,
                                        Model model) {
        String email = (String) session.getAttribute("email");
        
        if (email == null) {
            return "redirect:/forgot-password";
        }

        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "Confirmation password does not match!");
            return "auth/forgot-password-change";
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            // SỬ DỤNG SET PASSWORD HASH (LOMBOK)
            user.setPasswordHash(passwordEncoder.encode(newPassword)); 
            userRepository.save(user);
        }

        // Xóa session
        session.removeAttribute("otp");
        session.removeAttribute("email");
        session.removeAttribute("otpTime");

        return "redirect:/login?message=reset_success";
    }
    
    // --- RESEND OPT ---
    @GetMapping("/forgot-password/resend")
    public String resendOtp(HttpSession session, Model model) {
        String email = (String) session.getAttribute("email");
        
        if (email == null) {
            return "redirect:/forgot-password";
        }

        String otp = String.format("%06d", new Random().nextInt(999999));

        try {
            emailService.sendOtpEmail(email, otp);
        } catch (Exception e) {
            model.addAttribute("error", "Unable to send mail. Please try again later.");
            return "auth/forgot-password-otp";
        }

        session.setAttribute("otp", otp);
        session.setAttribute("otpTime", System.currentTimeMillis());

        // 6. Thêm thông báo thành công để hiển thị ở view
        model.addAttribute("message", "New verification code has been sent!");
        
        return "auth/forgot-password-otp";
    }
}