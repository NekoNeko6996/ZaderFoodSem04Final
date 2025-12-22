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

    public String storeBase64(String base64Str) {
        if (base64Str == null || base64Str.isEmpty()) {
            return null;
        }

        try {
            // 1. Tách phần header và data (VD: "data:image/png;base64,iVBOR...")
            String[] parts = base64Str.split(",");
            String header = parts[0];
            String data = parts.length > 1 ? parts[1] : parts[0];

            // 2. Xác định đuôi file (extension)
            String extension = ".jpg"; // Mặc định
            if (header.contains("png")) {
                extension = ".png";
            } else if (header.contains("jpeg")) {
                extension = ".jpeg";
            } else if (header.contains("gif")) {
                extension = ".gif";
            }

            // 3. Giải mã Base64 sang byte[]
            byte[] decodedBytes = java.util.Base64.getDecoder().decode(data);

            // 4. Tạo tên file ngẫu nhiên
            String fileName = UUID.randomUUID().toString() + extension;

            // 5. Lưu file vào ổ cứng
            Files.write(this.rootLocation.resolve(fileName), decodedBytes);

            // 6. Trả về đường dẫn web
            return "/uploads/" + fileName;

        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi lưu file Base64", e);
        }
    }
}
