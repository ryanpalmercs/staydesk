package com.staydesk.controller;

import com.staydesk.model.InventoryItem;
import com.staydesk.model.dto.InventoryBoardRowDto;
import com.staydesk.model.request.CreateInventoryItemRequest;
import com.staydesk.model.request.LogUsageRequest;
import com.staydesk.model.request.RestockRequest;
import com.staydesk.model.request.UpdateInventoryItemRequest;
import com.staydesk.service.InventoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("items")
    public List<InventoryBoardRowDto> getBoard() {
        return inventoryService.getBoard();
    }

    @GetMapping("low-stock")
    public List<InventoryBoardRowDto> getLowStockItems() {
        return inventoryService.getLowStockItems();
    }

    @PostMapping("items")
    public InventoryItem createItem(@RequestBody CreateInventoryItemRequest request) {
        return inventoryService.createItem(request);
    }

    @PutMapping("items/{itemId}")
    public InventoryItem updateItem(@PathVariable int itemId, @RequestBody UpdateInventoryItemRequest request) {
        return inventoryService.updateItem(itemId, request);
    }

    @PostMapping("items/{itemId}/restock")
    public void restock(@PathVariable int itemId, @RequestBody RestockRequest request,
                         @AuthenticationPrincipal Jwt jwt) {
        inventoryService.restock(itemId, request.quantity(), request.note(), UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("items/{itemId}/usage")
    public void logUsage(@PathVariable int itemId, @RequestBody LogUsageRequest request,
                         @AuthenticationPrincipal Jwt jwt) {
        inventoryService.logUsage(itemId, request.quantity(), request.roomId(), request.note(),
                UUID.fromString(jwt.getSubject()));
    }
}
