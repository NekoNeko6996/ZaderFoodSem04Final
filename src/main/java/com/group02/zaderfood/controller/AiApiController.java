package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.AiFoodResponse;
import com.group02.zaderfood.service.AiFoodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai")
public class AiApiController {

    @Autowired
    private AiFoodService aiFoodService;

    @PostMapping("/analyze")
    public ResponseEntity<AiFoodResponse> analyzeFood(
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        
        if ((text == null || text.isEmpty()) && (image == null || image.isEmpty())) {
            return ResponseEntity.badRequest().body(null);
        }

        AiFoodResponse result = aiFoodService.analyzeFood(text, image);
        return ResponseEntity.ok(result);
    }
}