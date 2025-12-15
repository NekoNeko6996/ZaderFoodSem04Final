package com.group02.zaderfood.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group02.zaderfood.dto.AiFoodResponse;
import com.group02.zaderfood.dto.PantryRecipeMatchDTO;
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
            List<Recipe> candidates = recipeRepository.findRandomNutritionistRecipes();
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

    public List<PantryRecipeMatchDTO> generateRecipesFromPantry(String ingredientsList, String userProfileInfo) {
        try {
            // --- PROMPT ĐƯỢC NÂNG CẤP ---
            String promptText = String.format(
                    "Role: Professional Chef & Nutritionist.\n" +
                    "Input: User's Pantry: [%s]. User Profile: %s.\n" +
                    "Task: Create EXACTLY 3 to 4 unique recipes.\n" +
                    "Constraints:\n" +
                    "1. OUTPUT RAW JSON ARRAY ONLY. NO Intro text. NO Note at the end. NO Markdown (```json).\n" + // Nhấn mạnh RAW JSON
                    "2. Structure must be a SINGLE Array of Objects: [ { ... }, { ... } ]\n" + // Nhấn mạnh Single Array
                    "3. Required Fields: name, description, ingredientsList, stepsList, timeMin, calories, servings, matchPercentage, missingCount.\n"
                    + "Output Structure:\n"
                    + "[ \n"
                    + "  { \n"
                    + "    \"name\": \"Dish Name\", \n"
                    + "    \"description\": \"Short appetizing description\", \n"
                    + "    \"timeMin\": 30, \n"
                    + "    \"calories\": 450, \n"
                    + "    \"servings\": 2, \n"
                    + "    \"ingredientsList\": [\"2 Eggs\", \"1 tbsp Oil\"], \n"
                    + "    \"stepsList\": [\"Step 1: Crack eggs\", \"Step 2: Fry\"], \n"
                    + "    \"matchPercentage\": 90, \n"
                    + "    \"missingCount\": 0, \n"
                    + "    \"imageUrl\": \"https://placehold.co/300?text=AI+Food\" \n"
                    + "  } \n"
                    + "]",
                    ingredientsList, userProfileInfo
            );

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "llama3"); // Hoặc model bạn đang dùng (mistral/gemma)
            requestBody.put("prompt", promptText);
            requestBody.put("stream", false);

            // Tăng context để AI không bị cắt chữ khi trả về nhiều trường
            requestBody.put("options", Map.of("temperature", 0.7, "num_ctx", 4096));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String rawResponse = getRestTemplate().postForObject(ollamaUrl, entity, String.class);
            System.out.println(rawResponse);

            return parsePantryAiResponse(rawResponse);

        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private List<PantryRecipeMatchDTO> parsePantryAiResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            if (root.has("response")) {
                String aiText = root.get("response").asText();

                // --- DEBUG: In ra để xem AI trả về gì ---
                System.out.println("AI Raw Text: " + aiText);

                // 1. Tách chuỗi JSON an toàn hơn Regex cũ
                String cleanJson = extractJsonArray(aiText);

                // 2. Parse thử thành JsonNode để kiểm tra cấu trúc
                JsonNode arrayNode = objectMapper.readTree(cleanJson);

                // 3. Xử lý trường hợp mảng lồng mảng [[...]]
                if (arrayNode.isArray() && arrayNode.size() > 0 && arrayNode.get(0).isArray()) {
                    System.out.println("AI returned nested array, unwrapping...");
                    // Lấy mảng bên trong ra để parse
                    cleanJson = arrayNode.get(0).toString();
                }

                // 4. Parse thành List DTO
                return objectMapper.readValue(cleanJson, new com.fasterxml.jackson.core.type.TypeReference<List<PantryRecipeMatchDTO>>() {
                });
            }
        } catch (Exception e) {
            System.err.println("AI Parse Error: " + e.getMessage());
            // e.printStackTrace(); // Bật lên nếu cần xem chi tiết
        }
        return Collections.emptyList();
    }

    // Hàm phụ trợ: Tìm JSON Array chuẩn xác trong văn bản hỗn loạn
    private String extractJsonArray(String text) {
        int start = text.indexOf("[");
        int end = text.lastIndexOf("]");

        if (start == -1 || end == -1 || end < start) {
            return "[]"; // Không tìm thấy JSON
        }

        return text.substring(start, end + 1);
    }
}
