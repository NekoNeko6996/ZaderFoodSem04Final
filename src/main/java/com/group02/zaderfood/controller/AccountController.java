package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.ChangePasswordDTO;
import com.group02.zaderfood.dto.UserProfileDTO;
import com.group02.zaderfood.service.CustomUserDetails; // Import class bạn vừa tạo
import com.group02.zaderfood.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountController {

    @Autowired
    private UserService userService;

    @GetMapping("/user/settings")
    public String showSettings(Model model,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            Integer userId = currentUser.getUserId();

            UserProfileDTO userProfileDTO = userService.getUserProfile(userId);
            model.addAttribute("userProfileDTO", userProfileDTO);
            model.addAttribute("changePasswordDTO", new ChangePasswordDTO());

            return "user/accountSetting";
        } catch (Exception e) {
            return "redirect:/error";
        }
    }

    @PostMapping("/user/settings")
    public String updateSettings(@ModelAttribute("userProfileDTO") UserProfileDTO dto,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            Integer userId = currentUser.getUserId();

            userService.updateUserProfile(userId, dto);

            redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating profile: " + e.getMessage());
        }
        return "redirect:/user/settings";
    }
    
    
    @PostMapping("/user/change-password")
    public String changePassword(@ModelAttribute("changePasswordDTO") ChangePasswordDTO dto,
                                 @AuthenticationPrincipal CustomUserDetails currentUser,
                                 RedirectAttributes redirectAttributes) {
        if (currentUser == null) return "redirect:/login";

        try {
            userService.changePassword(currentUser.getUserId(), dto);
            redirectAttributes.addFlashAttribute("successMessage", "");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        
        return "redirect:/user/settings";
    }
}
