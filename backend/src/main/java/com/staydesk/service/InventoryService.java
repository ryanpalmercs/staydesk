package com.staydesk.service;

import com.staydesk.exception.InventoryItemNotFoundException;
import com.staydesk.model.InventoryItem;
import com.staydesk.model.InventoryTransaction;
import com.staydesk.model.dto.InventoryBoardRowDto;
import com.staydesk.model.request.CreateInventoryItemRequest;
import com.staydesk.model.request.UpdateInventoryItemRequest;
import com.staydesk.repository.InventoryItemRepository;
import com.staydesk.repository.InventoryTransactionRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;

    public InventoryService(InventoryItemRepository inventoryItemRepository,
                             InventoryTransactionRepository inventoryTransactionRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
    }

    public List<InventoryBoardRowDto> getBoard() {
        Map<Integer, Integer> stockByItemId = computeStockByItemId();

        return inventoryItemRepository.findAllActive().stream()
                .sorted(Comparator.comparing(InventoryItem::name))
                .map(item -> toRow(item, stockByItemId.getOrDefault(item.id(), 0)))
                .toList();
    }

    public List<InventoryBoardRowDto> getLowStockItems() {
        return getBoard().stream()
                .filter(InventoryBoardRowDto::lowStock)
                .toList();
    }

    public InventoryItem createItem(CreateInventoryItemRequest request) {
        LocalDateTime now = LocalDateTime.now();
        return inventoryItemRepository.save(new InventoryItem(0, request.name(), request.category(), request.unit(),
                request.lowStockThreshold(), true, now, now));
    }

    public InventoryItem updateItem(int itemId, UpdateInventoryItemRequest request) {
        InventoryItem existing = inventoryItemRepository.findById(itemId)
                .orElseThrow(InventoryItemNotFoundException::new);

        return inventoryItemRepository.save(new InventoryItem(existing.id(), request.name(), request.category(),
                request.unit(), request.lowStockThreshold(), request.active(), existing.createdAt(), existing.updatedAt()));
    }

    public void restock(int itemId, int quantity, @Nullable String note, @Nullable UUID performedBy) {
        inventoryItemRepository.findById(itemId).orElseThrow(InventoryItemNotFoundException::new);

        inventoryTransactionRepository.save(new InventoryTransaction(0, itemId, InventoryTransaction.Type.RESTOCK,
                quantity, null, performedBy, note, LocalDateTime.now()));
    }

    public void logUsage(int itemId, int quantity, @Nullable Integer roomId, @Nullable String note,
                          @Nullable UUID performedBy) {
        inventoryItemRepository.findById(itemId).orElseThrow(InventoryItemNotFoundException::new);

        inventoryTransactionRepository.save(new InventoryTransaction(0, itemId, InventoryTransaction.Type.USAGE,
                quantity, roomId, performedBy, note, LocalDateTime.now()));
    }

    private Map<Integer, Integer> computeStockByItemId() {
        return inventoryTransactionRepository.findAll().stream()
                .collect(Collectors.groupingBy(InventoryTransaction::itemId,
                        Collectors.summingInt(t -> t.type() == InventoryTransaction.Type.RESTOCK
                                ? t.quantity() : -t.quantity())));
    }

    private InventoryBoardRowDto toRow(InventoryItem item, int currentStock) {
        return new InventoryBoardRowDto(item.id(), item.name(), item.category(), item.unit(), currentStock,
                item.lowStockThreshold(), currentStock <= item.lowStockThreshold());
    }
}
