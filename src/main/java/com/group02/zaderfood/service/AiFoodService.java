package com.group02.zaderfood.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
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

import com.group02.zaderfood.dto.WeeklyPlanDTO;
import com.group02.zaderfood.entity.Recipe;
import com.group02.zaderfood.repository.RecipeRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Service
public class AiFoodService {

    @Autowired
    private RecipeRepository recipeRepository;

    @Value("${ollama.host.url}") // Lấy từ application.properties
    private String ollamaUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private RestTemplate getRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000);
        factory.setReadTimeout(600000); // 10 phút
        return new RestTemplate(factory);
    }

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

            System.out.println("--- SENDING PROMPT TO AI ---");
            // Nhận về String thô để xử lý an toàn
            String rawResponse = restTemplate.postForObject(ollamaUrl, entity, String.class);
            System.err.println(rawResponse);

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
                String cleanJson = aiText
                        .replaceAll("(?i)```json", "") // Xóa ```json (không phân biệt hoa thường)
                        .replaceAll("```", "") // Xóa ```
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

    public WeeklyPlanDTO generateWeeklyPlan(int calories, String dietType, String goal) {
        try {
            // 1. Lấy dữ liệu: Giới hạn 40 món để giảm tải và tránh AI bị "ngáo" vì quá nhiều text
            List<Recipe> candidates = recipeRepository.findRandomRecipes();
            if (candidates.size() > 40) {
                candidates = candidates.subList(0, 40);
            }

            if (candidates.isEmpty()) {
                return null;
            }

            // 2. Format dữ liệu
            String recipeContext = candidates.stream()
                    .map(r -> String.format("{ID:%d, Name:'%s', Cal:%s}",
                    r.getRecipeId(),
                    r.getName().replace("'", ""), // Xóa dấu nháy đơn để tránh lỗi JSON
                    r.getTotalCalories()))
                    .collect(Collectors.joining(", "));

            // 3. PROMPT (Đã cập nhật Strict Mode)
            // 3. PROMPT (Đã cập nhật yêu cầu tính toán Calo)
            String promptText = String.format(
                    "Role: Nutritionist API. \n"
                    + "Context: I have these recipes: [%s].\n"
                    + "Task: Create a 7-day meal plan (Monday to Sunday) using ONLY the provided recipes.\n"
                    + "Constraints: \n"
                    + "1. User Diet: '%s'. Goal: %s.\n"
                    + "2. CALORIE TARGET: %d kcal/day. You MUST select 3 meals (Breakfast, Lunch, Dinner) such that their SUM is approximately equal to the target (allow +/- 200 kcal variance). Do NOT just pick random recipes.\n"
                    + "3. OUTPUT FULL JSON ONLY. Do NOT use markdown. Do NOT use '...'. Do NOT truncate.\n"
                    + "4. Ensure the JSON structure is exactly:\n"
                    + "{ \"days\": [ \n"
                    + "  { \"dayName\": \"Monday\", \"totalCalories\": 0, \"meals\": [ \n"
                    + "    { \"type\": \"Breakfast\", \"recipeId\": 123, \"recipeName\": \"Name\", \"calories\": 0 }, \n"
                    + "    { \"type\": \"Lunch\", \"recipeId\": 456, \"recipeName\": \"Name\", \"calories\": 0 }, \n"
                    + "    { \"type\": \"Dinner\", \"recipeId\": 789, \"recipeName\": \"Name\", \"calories\": 0 } \n"
                    + "  ] },\n"
                    + "  ... (Repeat for all 7 days) ... \n"
                    + "] }",
                    recipeContext, dietType, goal, calories // Lưu ý thứ tự tham số: calories nằm ở vị trí %d
            );

            // 4. Payload
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "llama3");
            requestBody.put("prompt", promptText);
            requestBody.put("stream", false);

            requestBody.put("options", Map.of(
                    "num_ctx", 8192, // Bộ nhớ ngữ cảnh tối đa của Llama3 (8k)
                    "num_predict", -1, // [QUAN TRỌNG] -1 nghĩa là KHÔNG GIỚI HẠN (viết đến khi xong thì thôi)
                    "temperature", 0.5 // Giữ nguyên để AI bớt sáng tạo linh tinh
            ));

            // 5. Gửi Request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            System.out.println("--- SENDING PROMPT TO AI ---");
            String rawResponse = getRestTemplate().postForObject(ollamaUrl, entity, String.class);

            return parseWeeklyPlanResponse(rawResponse);

        } catch (Exception e) {
            System.err.println("AI SERVICE ERROR: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private WeeklyPlanDTO parseWeeklyPlanResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            if (root.has("response")) {
                String aiText = root.get("response").asText();

                // --- DEBUG: IN RA CONSOLE ĐỂ XEM LỖI ---
                System.out.println("--- RAW AI RESPONSE ---");
                System.out.println(aiText);
                System.out.println("-----------------------");

                // CLEAN UP JSON
                String cleanJson = aiText.replaceAll("```json", "")
                        .replaceAll("```", "")
                        .replaceAll("//.*", "") // Xóa comment //
                        .trim();

                // Tìm điểm bắt đầu và kết thúc JSON hợp lệ
                int jsonStart = cleanJson.indexOf("{");
                int jsonEnd = cleanJson.lastIndexOf("}");

                if (jsonStart != -1 && jsonEnd != -1) {
                    cleanJson = cleanJson.substring(jsonStart, jsonEnd + 1);
                    WeeklyPlanDTO dto = objectMapper.readValue(cleanJson, WeeklyPlanDTO.class);

                    if (dto.days != null) {
                        for (WeeklyPlanDTO.DailyPlan day : dto.days) {
                            if (day.meals != null) {
                                int realTotal = 0;
                                for (WeeklyPlanDTO.Meal meal : day.meals) {
                                    realTotal += meal.calories;
                                }
                                day.totalCalories = realTotal;
                            }
                        }
                    }
                    // -------------------------------------

                    return dto;
                }
            }
        } catch (Exception e) {
            System.err.println("PARSE ERROR. Check RAW AI RESPONSE above.");
            e.printStackTrace();
        }
        return null;
    }
}
