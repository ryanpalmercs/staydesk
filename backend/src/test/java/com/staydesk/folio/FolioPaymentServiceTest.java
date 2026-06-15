package com.staydesk.folio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolioPaymentServiceTest {

    @Mock
    private FolioRepository folioRepository;

    @InjectMocks
    private FolioPaymentService service;

    @Test
    void marksOpenFolioPaidFromStripe() {
        UUID folioId = UUID.randomUUID();
        Folio folio = new Folio(folioId);
        Instant paidAt = Instant.parse("2026-06-15T04:47:49Z");

        when(folioRepository.findById(folioId)).thenReturn(Optional.of(folio));
        when(folioRepository.save(folio)).thenReturn(folio);

        Folio result = service.markPaidFromStripe(folioId, "pi_123", 12999L, paidAt);

        assertThat(result.getStatus()).isEqualTo(FolioStatus.PAID);
        assertThat(result.getStripePaymentIntentId()).isEqualTo("pi_123");
        assertThat(result.getPaidAmountCents()).isEqualTo(12999L);
        assertThat(result.getPaidAt()).isEqualTo(paidAt);
        verify(folioRepository).save(folio);
    }

    @Test
    void treatsSamePaymentIntentRetryAsIdempotent() {
        UUID folioId = UUID.randomUUID();
        Folio folio = new Folio(folioId);
        Instant paidAt = Instant.parse("2026-06-15T04:47:49Z");
        folio.markPaid("pi_123", 12999L, paidAt);

        when(folioRepository.findById(folioId)).thenReturn(Optional.of(folio));

        Folio result = service.markPaidFromStripe(folioId, "pi_123", 12999L, paidAt);

        assertThat(result).isSameAs(folio);
        verify(folioRepository, never()).save(folio);
    }

    @Test
    void rejectsDifferentPaymentIntentForAlreadyPaidFolio() {
        UUID folioId = UUID.randomUUID();
        Folio folio = new Folio(folioId);
        folio.markPaid("pi_first", 12999L, Instant.parse("2026-06-15T04:47:49Z"));

        when(folioRepository.findById(folioId)).thenReturn(Optional.of(folio));

        assertThatThrownBy(() -> service.markPaidFromStripe(
                folioId,
                "pi_second",
                12999L,
                Instant.parse("2026-06-15T04:50:00Z")
        )).isInstanceOf(FolioAlreadyPaidException.class);

        verify(folioRepository, never()).save(folio);
    }

    @Test
    void rejectsUnknownFolio() {
        UUID folioId = UUID.randomUUID();
        when(folioRepository.findById(folioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markPaidFromStripe(
                folioId,
                "pi_123",
                12999L,
                Instant.parse("2026-06-15T04:47:49Z")
        )).isInstanceOf(FolioNotFoundException.class);
    }
}
