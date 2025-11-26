package com.group02.zaderfood.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;


@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("lqm231231@gmail.com");
        message.setTo(toEmail);
        message.setSubject("Forgot Password OTP Code");
        message.setText("Your OTP Code: " + otp + "\nThis code will expire in 5 minutes.");
        mailSender.send(message);
    }
}