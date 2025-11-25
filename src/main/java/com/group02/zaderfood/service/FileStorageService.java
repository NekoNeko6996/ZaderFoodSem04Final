package com.group02.zaderfood.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {
    
    // Lưu file vào thư mục "uploads" nằm ở thư mục gốc dự án
    private final Path rootLocation = Paths.get("uploads");

    public FileStorageService() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Không thể khởi tạo thư mục lưu trữ!", e);
        }
    }

    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            // Tạo tên file ngẫu nhiên để tránh trùng: uuid_tenfilegoc.jpg
            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Files.copy(file.getInputStream(), this.rootLocation.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            
            // Trả về đường dẫn để lưu vào DB (ví dụ: /uploads/abc.jpg)
            return "/uploads/" + fileName;
        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi lưu file: " + file.getOriginalFilename(), e);
        }
    }
}