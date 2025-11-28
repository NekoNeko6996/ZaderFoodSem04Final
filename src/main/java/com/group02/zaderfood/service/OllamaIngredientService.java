package com.group02.zaderfood.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group02.zaderfood.dto.IngredientAnalysisDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
public class OllamaIngredientService {

    @Value("${ollama.host.url}") 
    private String ollamaUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IngredientAnalysisDTO analyzeIngredient(String name, String category, String baseUnit, MultipartFile imageFile) {
        try {
            // 1. Chuyển ảnh sang Base64 (Nếu có)
            String base64Image = null;
            if (imageFile != null && !imageFile.isEmpty()) {
                base64Image = Base64.getEncoder().encodeToString(imageFile.getBytes());
            }

            // 2. Tạo Prompt (Tối ưu cho LLaVA để lấy Macros)
            String promptText = String.format(
                "You are a nutrition expert. Analyze this ingredient: '%s' (Category: %s). " +
                "Base unit is: %s. " +
                "Estimate the average nutritional values for 100%s of this ingredient based on the name and image (if provided). " +
                "Return ONLY a JSON object with these keys: 'calories' (number), 'protein' (number), 'fat' (number), 'carbs' (number). " +
                "Do not include any explanation or markdown formatting like ```json. Just raw JSON.", 
                name, category, baseUnit, baseUnit
            );

            // 3. Tạo Payload gửi Ollama
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "llava"); // Dùng model LLaVA
            requestBody.put("prompt", promptText);
            requestBody.put("stream", false); // Tắt stream để nhận 1 cục JSON
            
            if (base64Image != null) {
                requestBody.put("images", Collections.singletonList(base64Image));
            }

            // 4. Gửi Request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String rawResponse = restTemplate.postForObject(ollamaUrl, entity, String.class);

            // 5. Parse kết quả
            return parseOllamaResponse(rawResponse);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private IngredientAnalysisDTO parseOllamaResponse(String jsonResponse) {
        try {
            // Ollama trả về: { "response": "{ \"calories\": ... }" }
            JsonNode root = objectMapper.readTree(jsonResponse);
            
            if (root.has("response")) {
                String aiText = root.get("response").asText();
                
                // Làm sạch Markdown nếu AI lỡ thêm vào
                String cleanJson = aiText.replaceAll("```json", "")
                                         .replaceAll("```", "")
                                         .trim();

                // Cắt lấy phần JSON thuần túy nhất
                int jsonStart = cleanJson.indexOf("{");
                int jsonEnd = cleanJson.lastIndexOf("}");
                
                if (jsonStart != -1 && jsonEnd != -1) {
                    cleanJson = cleanJson.substring(jsonStart, jsonEnd + 1);
                    return objectMapper.readValue(cleanJson, IngredientAnalysisDTO.class);
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing Ollama response: " + e.getMessage());
        }
        return null;
    }
}