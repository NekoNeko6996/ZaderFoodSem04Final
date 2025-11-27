package com.group02.zaderfood.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group02.zaderfood.dto.AiFoodResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.client.RestClientException;

@Service
public class AiFoodService {

    @Value("${ollama.host.url}") // Lấy từ application.properties
    private String ollamaUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiFoodResponse analyzeFood(String textDescription, MultipartFile imageFile) {
        try {
            // 1. Chuyển ảnh sang Base64
            String base64Image = null;
            if (imageFile != null && !imageFile.isEmpty()) {
                String contentType = imageFile.getContentType();
                if (contentType == null
                        || (!contentType.equals("image/jpeg") && !contentType.equals("image/png") && !contentType.equals("image/jpg"))) {
                    return createErrorResponse("Invalid file type. Only PNG and JPG are allowed.");
                }

                base64Image = Base64.getEncoder().encodeToString(imageFile.getBytes());
            }

            // 2. Tối ưu Prompt: Bỏ bớt ràng buộc "Not food" để tránh false-negative
            // Yêu cầu AI đóng vai chuyên gia dinh dưỡng nhiệt tình.
            String promptText = "You are a nutrition expert. Analyze this food image carefully. "
                    + "Generate a JSON object containing nutritional data. "
                    + "Use this exact structure: "
                    + "{ \"dishName\": \"string\", \"calories\": int, \"protein\": \"string\", "
                    + "\"carbs\": \"string\", \"fat\": \"string\", \"time\": \"string\", "
                    + "\"ingredients\": [\"string\"], \"instructions\": [\"string\"] }. "
                    + "Do not include markdown formatting like ```json. Just raw JSON.";

            if (textDescription != null && !textDescription.isEmpty()) {
                promptText = "Dish hint: " + textDescription + ". " + promptText;
            }

            // 3. Tạo Payload đúng chuẩn Ollama
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "llava");
            requestBody.put("prompt", promptText);
            requestBody.put("stream", false);
            if (base64Image != null) {
                requestBody.put("images", Collections.singletonList(base64Image));
            }

            // 4. Gửi Request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Nhận về String thô để xử lý an toàn
            String rawResponse = restTemplate.postForObject(ollamaUrl, entity, String.class);

            // 5. Xử lý kết quả
            return parseOllamaResponse(rawResponse);

        } catch (IOException e) {
            return createErrorResponse("Error processing file: " + e.getMessage());
        } catch (RestClientException e) {
            return createErrorResponse("AI Service Error: " + e.getMessage());
        }
    }

    private AiFoodResponse parseOllamaResponse(String jsonResponse) {
        try {
            // Ollama trả về JSON dạng: { "model":..., "response": "Nội dung AI trả lời..." }
            JsonNode root = objectMapper.readTree(jsonResponse);

            if (root.has("response")) {
                String aiText = root.get("response").asText();

                // Làm sạch: Xóa markdown ```json và ``` nếu AI lỡ thêm vào
                String cleanJson = aiText.replaceAll("```json", "")
                        .replaceAll("```", "")
                        .trim();

                // Tìm điểm bắt đầu '{' và kết thúc '}' để lấy đúng phần JSON
                int jsonStart = cleanJson.indexOf("{");
                int jsonEnd = cleanJson.lastIndexOf("}");
                if (jsonStart != -1 && jsonEnd != -1) {
                    cleanJson = cleanJson.substring(jsonStart, jsonEnd + 1);
                    return objectMapper.readValue(cleanJson, AiFoodResponse.class);
                } else {
                    // Trường hợp AI nói lung tung không ra JSON
                    return createErrorResponse("AI identified: " + aiText);
                }
            }
        } catch (JsonProcessingException e) {
        }
        return createErrorResponse("Failed to parse AI response.");
    }

    private AiFoodResponse createErrorResponse(String errorMsg) {
        AiFoodResponse response = new AiFoodResponse();
        response.setError(errorMsg);
        return response;
    }
}
