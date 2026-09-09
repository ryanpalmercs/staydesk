package com.staydesk.service;

import com.staydesk.exception.ExtraNotFoundException;
import com.staydesk.exception.FolioClosedException;
import com.staydesk.exception.FolioNotFoundException;
import com.staydesk.exception.ReservationNotFoundException;
import com.staydesk.model.Extra;
import com.staydesk.model.Folio;
import com.staydesk.model.FolioItem;
import com.staydesk.model.Reservation;
import com.staydesk.repository.ExtraRepository;
import com.staydesk.repository.FolioItemRepository;
import com.staydesk.repository.FolioRepository;
import com.staydesk.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FolioService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FolioService.class);

    private final FolioRepository folioRepository;
    private final FolioItemRepository folioItemRepository;
    private final ExtraRepository extraRepository;
    private final ReservationRepository reservationRepository;
    private final PropertySettingsService propertySettingsService;

    public FolioService(FolioRepository folioRepository, FolioItemRepository folioItemRepository,
                        ExtraRepository extraRepository, ReservationRepository reservationRepository,
                        PropertySettingsService propertySettingsService) {
        this.folioRepository = folioRepository;
        this.folioItemRepository = folioItemRepository;
        this.extraRepository = extraRepository;
        this.reservationRepository = reservationRepository;
        this.propertySettingsService = propertySettingsService;
    }

    private BigDecimal taxOn(BigDecimal amount) {
        String taxRateString = propertySettingsService.getProperty("lodging_tax_rate").value();
        BigDecimal taxRate = BigDecimal.ZERO;

        try {
            taxRate = new BigDecimal(taxRateString);
        } catch (NumberFormatException e) {
            LOGGER.error("Could not parse tax rate from property settings.", e);
        }

        return amount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal estimateWithTax(BigDecimal amount) {
        return amount.add(taxOn(amount));
    }

    @Transactional
    public Folio postCharge(Folio folio, String description, BigDecimal amount) {
        return postCharge(folio, description, amount, null, null);
    }

    @Transactional
    public Folio postCharge(Folio folio, String description, BigDecimal amount, Integer extraId, Integer quantity) {
        LocalDateTime now = LocalDateTime.now();

        folioItemRepository.save(new FolioItem(0, folio.id(), description, amount, FolioItem.FolioItemType.CHARGE, extraId, quantity, now, now));

        BigDecimal tax = taxOn(amount);
        folioItemRepository.save(new FolioItem(0, folio.id(), description + " TAX", tax, FolioItem.FolioItemType.TAX, null, null, now, now));

        BigDecimal newTotal = folio.total().add(amount).add(tax);

        return folioRepository.save(new Folio(folio.id(), folio.reservationId(), folio.status(), newTotal, folio.paidAt(), folio.createdAt(), now));
    }

    public record PerNightExtraCharge(int extraId, String extraName, BigDecimal unitPrice, int quantity) {
    }

    public record ExtraSelection(int extraId, int quantity) {
    }

    public BigDecimal priceExtras(List<ExtraSelection> selections, long nights) {
        BigDecimal subtotal = BigDecimal.ZERO;

        for (ExtraSelection selection : selections) {
            Extra extra = extraRepository.findById(selection.extraId()).orElseThrow(ExtraNotFoundException::new);
            BigDecimal units = BigDecimal.valueOf(selection.quantity());

            if (extra.billingType() == Extra.BillingType.PER_NIGHT) {
                units = units.multiply(BigDecimal.valueOf(nights));
            }

            subtotal = subtotal.add(extra.price().multiply(units));
        }

        return subtotal;
    }

    public List<PerNightExtraCharge> distinctPerNightExtras(int folioId) {
        Map<Integer, FolioItem> firstItemByExtraId = new LinkedHashMap<>();

        for (FolioItem item : folioItemRepository.findByFolioId(folioId)) {
            if (item.extraId() != null) {
                firstItemByExtraId.putIfAbsent(item.extraId(), item);
            }
        }

        List<PerNightExtraCharge> charges = new ArrayList<>();

        for (FolioItem item : firstItemByExtraId.values()) {
            extraRepository.findById(item.extraId())
                    .filter(extra -> extra.billingType() == Extra.BillingType.PER_NIGHT)
                    .ifPresent(extra -> charges.add(new PerNightExtraCharge(extra.id(), extra.name(), extra.price(), item.quantity())));
        }

        return charges;
    }

    public long countRoomChargesPosted(int folioId) {
        return folioItemRepository.findByFolioId(folioId).stream()
                .filter(item -> item.type() == FolioItem.FolioItemType.CHARGE)
                .filter(item -> "GUEST ROOM".equals(item.description()))
                .count();
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

        String description = (quantity > 1 ? extra.name() + " x" + quantity : extra.name()).toUpperCase();
        BigDecimal amount = extra.price().multiply(BigDecimal.valueOf(quantity));

        if (extra.billingType() == Extra.BillingType.PER_NIGHT) {
            Reservation reservation = reservationRepository.findById(folio.reservationId())
                    .orElseThrow(ReservationNotFoundException::new);
            long nights = ChronoUnit.DAYS.between(reservation.checkInDate(), reservation.checkOutDate());

            for (long i = 0; i < nights; i++) {
                folio = postCharge(folio, description, amount, extra.id(), quantity);
            }

            return folio;
        }

        return postCharge(folio, description, amount, extra.id(), quantity);
    }

    @Transactional
    public Folio postIncidentCharge(Folio folio, String description, BigDecimal amount) {
        return postCharge(folio, description, amount);
    }
}
