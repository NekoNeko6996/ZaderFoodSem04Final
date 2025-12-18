package com.group02.zaderfood.controller;

import com.group02.zaderfood.service.CustomUserDetails;
import com.group02.zaderfood.service.MealPlanService; // Để lấy ngày đã plan
import com.group02.zaderfood.service.ShoppingListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.group02.zaderfood.entity.ShoppingList;
import java.io.ByteArrayInputStream;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/shopping-list")
public class ShoppingListController {

    @Autowired
    private ShoppingListService shoppingService;
    @Autowired
    private MealPlanService mealPlanService; // Cần service này để lấy list ngày có plan

    @GetMapping
    public String viewShoppingPage(Model model,
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer listId) {
        if (user == null) {
            return "redirect:/login";
        }
        Integer userId = user.getUserId();

        // 1. Lấy danh sách ngày đã Plan (Logic cũ)
        List<String> plannedDates = mealPlanService.getUpcomingPlannedDates(userId)
                .stream().map(LocalDate::toString).collect(Collectors.toList());
        model.addAttribute("plannedDates", plannedDates);

        // 2. Nếu đang xem một List cụ thể
        if (listId != null) {
            // [NEW] Lấy thông tin List để hiển thị lại ngày trên lịch
            ShoppingList list = shoppingService.getListById(listId); // Cần thêm hàm này ở Service
            if (list != null && list.getUserId().equals(userId)) {
                model.addAttribute("currentList", list);
                model.addAttribute("fromDateStr", list.getFromDate().toString());
                model.addAttribute("toDateStr", list.getToDate().toString());
            }

            var groupedItems = shoppingService.getListDetailsGrouped(listId);
            model.addAttribute("groupedItems", groupedItems);
            model.addAttribute("currentListId", listId);
        }

        return "shopping/index";
    }

    // [NEW] API XUẤT EXCEL
    @GetMapping("/{listId}/export")
    public ResponseEntity<InputStreamResource> exportExcel(@PathVariable Integer listId) {
        try {
            ByteArrayInputStream in = shoppingService.exportToExcel(listId);
            InputStreamResource resource = new InputStreamResource(in);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=shopping_list.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // [NEW] API GỬI EMAIL
    @PostMapping("/{listId}/email")
    public ResponseEntity<?> sendEmail(@PathVariable Integer listId, @AuthenticationPrincipal CustomUserDetails user) {
        try {
            shoppingService.sendListViaEmail(user.getUserId(), listId);
            return ResponseEntity.ok(Map.of("message", "Email sent successfully!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/generate")
    public String generateList(@AuthenticationPrincipal CustomUserDetails user,
            @RequestParam String dateRange) { // Format: "2025-12-18 to 2025-12-25"
        if (user == null) {
            return "redirect:/login";
        }

        // Parse Flatpickr range string
        String[] dates = dateRange.split(" to ");
        if (dates.length < 2) {
            return "redirect:/shopping-list?error=invalid_range";
        }

        LocalDate from = LocalDate.parse(dates[0]);
        LocalDate to = LocalDate.parse(dates[1]);

        ShoppingList list = shoppingService.generateOrGetList(user.getUserId(), from, to);

        return "redirect:/shopping-list?listId=" + list.getListId();
    }

    @PostMapping("/api/check")
    @ResponseBody
    public ResponseEntity<?> toggleCheck(@RequestParam Integer itemId) {
        shoppingService.toggleItemStatus(itemId);
        return ResponseEntity.ok().build();
    }
}
