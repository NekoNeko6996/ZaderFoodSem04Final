package com.group02.zaderfood.service;

import com.group02.zaderfood.dto.DayDetailDTO;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;


@Service
public class EmailService {
    
    @Autowired
    private JavaMailSender mailSender;
    
    @Autowired
    private MealPlanService mealPlan;
    
    @Autowired 
    private com.group02.zaderfood.repository.UserRepository userRepo; 

    public void sendOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("lqm231231@gmail.com");
        message.setTo(toEmail);
        message.setSubject("Forgot Password OTP Code");
        message.setText("Your OTP Code: " + otp + "\nThis code will expire in 5 minutes.");
        mailSender.send(message);
    }
    
    public void sendShoppingListEmail(Integer userId, LocalDate date) throws Exception {
        // Lấy thông tin User
        com.group02.zaderfood.entity.User user = userRepo.findById(userId)
            .orElseThrow(() -> new Exception("User not found"));
            
        // Lấy dữ liệu
        DayDetailDTO dayDetail = mealPlan.getDayDetail(userId, date);
        List<DayDetailDTO.IngredientSummary> ingredients = dayDetail.dailyIngredients;

        // Tạo nội dung HTML Table
        StringBuilder htmlMsg = new StringBuilder();
        htmlMsg.append("<h3>Hello ").append(user.getFullName()).append(",</h3>");
        htmlMsg.append("<p>Here is your shopping list for <b>").append(date).append("</b>:</p>");
        htmlMsg.append("<table border='1' style='border-collapse: collapse; width: 100%;'>");
        htmlMsg.append("<tr style='background-color: #f2f2f2;'><th>Ingredient</th><th>Quantity</th></tr>");
        
        for (DayDetailDTO.IngredientSummary item : ingredients) {
            htmlMsg.append("<tr>");
            htmlMsg.append("<td style='padding: 8px;'>").append(item.name).append("</td>");
            htmlMsg.append("<td style='padding: 8px;'>").append(item.quantity).append("</td>");
            htmlMsg.append("</tr>");
        }
        htmlMsg.append("</table>");
        htmlMsg.append("<p>Happy Cooking!<br>ZaderFood Team</p>");

        // Gửi Mail
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setTo(user.getEmail());
        helper.setSubject("Your Shopping List for " + date);
        helper.setText(htmlMsg.toString(), true); // true = html content
        
        mailSender.send(message);
    }
}