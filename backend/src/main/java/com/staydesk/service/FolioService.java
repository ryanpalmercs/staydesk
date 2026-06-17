package com.staydesk.service;

import com.staydesk.exception.ExtraNotFoundException;
import com.staydesk.exception.FolioClosedException;
import com.staydesk.exception.FolioNotFoundException;
import com.staydesk.model.Extra;
import com.staydesk.model.Folio;
import com.staydesk.model.FolioItem;
import com.staydesk.repository.ExtraRepository;
import com.staydesk.repository.FolioItemRepository;
import com.staydesk.repository.FolioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class FolioService {

    private final FolioRepository folioRepository;
    private final FolioItemRepository folioItemRepository;
    private final ExtraRepository extraRepository;

    @Value("${app.lodging-tax-rate}")
    private BigDecimal lodgingTaxRate;

    public FolioService(FolioRepository folioRepository, FolioItemRepository folioItemRepository,
                        ExtraRepository extraRepository) {
        this.folioRepository = folioRepository;
        this.folioItemRepository = folioItemRepository;
        this.extraRepository = extraRepository;
    }

    private BigDecimal taxOn(BigDecimal amount) {
        return amount.multiply(lodgingTaxRate).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal estimateWithTax(BigDecimal amount) {
        return amount.add(taxOn(amount));
    }

    @Transactional
    public Folio postCharge(Folio folio, String description, BigDecimal amount) {
        LocalDateTime now = LocalDateTime.now();

        folioItemRepository.save(new FolioItem(0, folio.id(), description, amount, FolioItem.FolioItemType.CHARGE, now, now));

        BigDecimal tax = taxOn(amount);
        folioItemRepository.save(new FolioItem(0, folio.id(), description + " TAX", tax, FolioItem.FolioItemType.TAX, now, now));

        BigDecimal newTotal = folio.total().add(amount).add(tax);

        return folioRepository.save(new Folio(folio.id(), folio.reservationId(), folio.status(), newTotal, folio.paidAt(), folio.createdAt(), now));
    }

    @Transactional
    public Folio addExtra(int folioId, int extraId, int quantity) {
        Folio folio = folioRepository.findById(folioId).orElseThrow(FolioNotFoundException::new);

        if (folio.status() != Folio.FolioStatus.OPEN) {
            throw new FolioClosedException();
        }

        Extra extra = extraRepository.findById(extraId).orElseThrow(ExtraNotFoundException::new);

        if (!extra.active()) {
            throw new ExtraNotFoundException();
        }

        BigDecimal amount = extra.price().multiply(BigDecimal.valueOf(quantity));
        String description = quantity > 1 ? extra.name() + " x" + quantity : extra.name();

        return postCharge(folio, description, amount);
    }
}
