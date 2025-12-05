package com.group02.zaderfood.service;

import com.group02.zaderfood.entity.User;
import com.group02.zaderfood.entity.UserDietaryPreference;
import com.group02.zaderfood.entity.UserProfile;
import com.group02.zaderfood.entity.enums.UserRole;
import com.group02.zaderfood.entity.enums.UserStatus;
import com.group02.zaderfood.repository.UserDietaryPreferenceRepository;
import com.group02.zaderfood.repository.UserProfileRepository;
import com.group02.zaderfood.repository.UserRepository;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder; // Giả sử bạn dùng Spring Security
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private UserDietaryPreferenceRepository dietRepo;

    // 1. Lấy danh sách user có phân trang và lọc
    public Page<User> getUsers(Integer currentUserId, String keyword, UserRole role, UserStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        // Truyền currentUserId vào Repository
        return userRepository.searchUsers(currentUserId, keyword, role, status, pageable);
    }

    // 2. Thêm người dùng mới
    public void createUser(User user, String rawPassword) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        // Cập nhật: Mã hóa password được truyền vào
        user.setPasswordHash(passwordEncoder.encode(rawPassword));

        user.setCreatedAt(LocalDateTime.now());
        user.setIsDeleted(false);
        user.setIsEmailVerified(true);
        userRepository.save(user);
    }

    // 3. Đổi trạng thái (Block/Active)
    public void toggleUserStatus(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() == UserStatus.BANNED) {
            throw new RuntimeException("Cannot unban a banned user.");
        }

        if (user.getStatus() == UserStatus.BLOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        } else {
            user.setStatus(UserStatus.BLOCKED);
        }
        userRepository.save(user);
    }

    public void banUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setStatus(UserStatus.BANNED);
        userRepository.save(user);
    }

    // 4. Xuất Excel
    public void exportUsersToExcel(HttpServletResponse response) throws IOException {
        Page<User> users = userRepository.findAll(PageRequest.of(0, 10000));

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Users & Profiles");

        // Header Row cập nhật thêm cột Profile
        Row headerRow = sheet.createRow(0);
        String[] columns = {
            "ID", "Full Name", "Email", "Role", "Status", "Joined Date", // User info
            "Gender", "BirthDate", "Weight(kg)", "Height(cm)", "Activity Level", "Diet Type" // Profile info
        };

        // Style cho Header (in đậm)
        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data Rows
        int rowNum = 1;
        for (User user : users) {
            Row row = sheet.createRow(rowNum++);

            // Thông tin cơ bản từ bảng User
            row.createCell(0).setCellValue(user.getUserId());
            row.createCell(1).setCellValue(user.getFullName());
            row.createCell(2).setCellValue(user.getEmail());
            row.createCell(3).setCellValue(user.getRole().name());
            row.createCell(4).setCellValue(user.getStatus().name());
            row.createCell(5).setCellValue(user.getCreatedAt().toString().split("T")[0]);

            Optional<UserProfile> profileOpt = userProfileRepository.findById(user.getUserId());

            if (profileOpt.isPresent()) {
                UserProfile profile = profileOpt.get();
                row.createCell(6).setCellValue(profile.getGender() != null ? profile.getGender().name() : "N/A");
                row.createCell(7).setCellValue(profile.getBirthDate() != null ? profile.getBirthDate().toString() : "N/A");
                row.createCell(8).setCellValue(profile.getWeightKg() != null ? profile.getWeightKg().toString() : "0");
                row.createCell(9).setCellValue(profile.getHeightCm() != null ? profile.getHeightCm().toString() : "0");
                row.createCell(10).setCellValue(profile.getActivityLevel() != null ? profile.getActivityLevel().name() : "N/A");

                // [FIX] Lấy danh sách chế độ ăn từ bảng mới và nối chuỗi
                List<UserDietaryPreference> diets = dietRepo.findByUserId(user.getUserId());
                if (!diets.isEmpty()) {
                    // Ví dụ kết quả: "KETO, GLUTEN_FREE"
                    String dietString = diets.stream()
                            .map(d -> d.getDietType().name())
                            .collect(Collectors.joining(", "));
                    row.createCell(11).setCellValue(dietString);
                } else {
                    row.createCell(11).setCellValue("N/A");
                }

            } else {
                for (int k = 6; k <= 11; k++) {
                    row.createCell(k).setCellValue("N/A");
                }
            }
        }

        // Auto size cột cho đẹp
        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=users_full_export.xlsx");
        ServletOutputStream outputStream = response.getOutputStream();
        workbook.write(outputStream);
        workbook.close();
        outputStream.close();
    }
}
