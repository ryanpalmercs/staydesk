package com.staydesk.service;

import com.staydesk.exception.GuestNotFoundException;
import com.staydesk.exception.InvalidReservationException;
import com.staydesk.exception.NoRoomAvailableException;
import com.staydesk.exception.RemoteCheckInTokenInvalidException;
import com.staydesk.exception.ReservationNotFoundException;
import com.staydesk.exception.RoomTypeNotFoundException;
import com.staydesk.model.Guest;
import com.staydesk.model.RemoteCheckInToken;
import com.staydesk.model.Reservation;
import com.staydesk.model.Room;
import com.staydesk.model.RoomType;
import com.staydesk.model.dto.CheckInResult;
import com.staydesk.model.dto.RemoteCheckInSummary;
import com.staydesk.repository.GuestRepository;
import com.staydesk.repository.RemoteCheckInTokenRepository;
import com.staydesk.repository.ReservationRepository;
import com.staydesk.repository.RoomRepository;
import com.staydesk.repository.RoomTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Guest-facing remote check-in (#64): a secure, single-use token is delivered by SMS/email once
 * the reservation's check-in date arrives (not at booking time -- sendDueRemoteCheckInLinks runs
 * daily and picks up reservations checking in that day). Completing it delegates room assignment,
 * remaining-balance charging, the incidental hold, and door-code issuance to
 * {@link ReservationService#checkIn} -- the same path WALK_IN/PHONE front-desk check-in uses, per
 * #121's deferred room-type model. Only applies to PHONE/ONLINE reservations, which already
 * charged the full stay at booking -- WALK_IN guests are in person by definition.
 *
 * Reservation status flow is CONFIRMED -> CHECKED_IN, same as WALK_IN/PHONE -- remote check-in is
 * just another path to CHECKED_IN, not a distinct status.
 *
 * Gated behind remote-checkin.enabled (REMOTE_CHECKIN_ENABLED env var, off by default, same
 * pattern as payment.card-present.record-only) until the Sifely locks are installed in every
 * room -- completion issues a door code, which can't work on a room with no lock yet.
 */
@Service
public class RemoteCheckInService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCheckInService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RemoteCheckInTokenRepository remoteCheckInTokenRepository;
    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final GuestRepository guestRepository;
    private final ReservationService reservationService;
    private final SmsService smsService;
    private final EmailService emailService;
    private final boolean remoteCheckInEnabled;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public RemoteCheckInService(RemoteCheckInTokenRepository remoteCheckInTokenRepository,
                                ReservationRepository reservationRepository, RoomRepository roomRepository,
                                RoomTypeRepository roomTypeRepository, GuestRepository guestRepository,
                                ReservationService reservationService, SmsService smsService, EmailService emailService,
                                @Value("${remote-checkin.enabled:false}") boolean remoteCheckInEnabled) {
        this.remoteCheckInTokenRepository = remoteCheckInTokenRepository;
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.guestRepository = guestRepository;
        this.reservationService = reservationService;
        this.smsService = smsService;
        this.emailService = emailService;
        this.remoteCheckInEnabled = remoteCheckInEnabled;
    }

    private static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Chicago")
    public void sendDueRemoteCheckInLinks() {
        if (!remoteCheckInEnabled) {
            return;
        }

        LocalDate today = LocalDate.now(ZoneId.of("America/Chicago"));
        List<Reservation> candidates = reservationRepository.findDueForRemoteCheckInLink(today);
        int sent = 0;

        for (Reservation reservation : candidates) {
            if (reservation.guestId() == null || remoteCheckInTokenRepository.existsByReservationId(reservation.id())) {
                continue;
            }

            try {
                guestRepository.findById(reservation.guestId()).ifPresent(guest -> generateAndSendLink(reservation, guest));
                sent++;
            } catch (Exception e) {
                LOGGER.error("Failed to send remote check-in link for reservation {}", reservation.id(), e);
            }
        }

        LOGGER.info("Remote check-in link job completed. {}/{} link(s) sent.", sent, candidates.size());
    }

    @Transactional
    public void generateAndSendLink(Reservation reservation, Guest guest) {
        if (reservation.channel() == Reservation.Channel.WALK_IN) {
            return;
        }

        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = reservation.checkOutDate().atStartOfDay();

        remoteCheckInTokenRepository.save(new RemoteCheckInToken(0, reservation.id(), hashToken(rawToken), expiresAt, null, now));

        String link = frontendUrl + "/remote-check-in/" + rawToken;

        if (guest.smsConsent()) {
            smsService.sendCheckInLink(guest, reservation, link);
        }
        emailService.sendCheckInLink(guest, reservation, link);
    }

    private RemoteCheckInToken resolveValidToken(String rawToken) {
        if (!remoteCheckInEnabled) {
            throw new RemoteCheckInTokenInvalidException();
        }

        RemoteCheckInToken token = remoteCheckInTokenRepository.findByTokenHash(hashToken(rawToken))
                                                               .orElseThrow(RemoteCheckInTokenInvalidException::new);

        if (token.usedAt() != null || token.expiresAt().isBefore(LocalDateTime.now())) {
            throw new RemoteCheckInTokenInvalidException();
        }

        return token;
    }

    public RemoteCheckInSummary getSummaryForToken(String rawToken) {
        RemoteCheckInToken token = resolveValidToken(rawToken);

        Reservation reservation = reservationRepository.findById(token.reservationId())
                                                        .orElseThrow(ReservationNotFoundException::new);

        if (reservation.status() != Reservation.ReservationStatus.CONFIRMED) {
            throw new RemoteCheckInTokenInvalidException();
        }

        RoomType roomType = roomTypeRepository.findById(reservation.roomTypeId()).orElseThrow(RoomTypeNotFoundException::new);

        String guestFirstName = reservation.guestId() != null
                ? guestRepository.findById(reservation.guestId()).map(g -> g.firstName().value()).orElse("")
                : "";

        return new RemoteCheckInSummary(reservation.confirmationCode(), roomType.name(), reservation.checkInDate(),
                reservation.checkOutDate(), reservation.guestCount(), guestFirstName);
    }

    @Transactional
    public Reservation completeRemoteCheckIn(String rawToken, String incidentalsPaymentMethodId, boolean agreedToTerms) {
        if (!agreedToTerms) {
            throw new InvalidReservationException();
        }

        RemoteCheckInToken token = resolveValidToken(rawToken);

        Reservation reservation = reservationRepository.findById(token.reservationId())
                                                        .orElseThrow(ReservationNotFoundException::new);

        if (reservation.status() != Reservation.ReservationStatus.CONFIRMED || reservation.channel() == Reservation.Channel.WALK_IN) {
            throw new InvalidReservationException();
        }

        Room room = roomRepository.findAvailableOfType(reservation.roomTypeId(), reservation.checkOutDate(), reservation.checkInDate(),
                                                        reservation.id())
                                  .stream()
                                  .findFirst()
                                  .orElseThrow(NoRoomAvailableException::new);

        CheckInResult checkInResult = reservationService.checkIn(reservation.id(), room.id(), incidentalsPaymentMethodId, null);
        Reservation checkedIn = checkInResult.reservation();

        remoteCheckInTokenRepository.markUsed(token.id());

        if (reservation.guestId() != null) {
            Guest guest = guestRepository.findById(reservation.guestId()).orElseThrow(GuestNotFoundException::new);
            emailService.sendFrontDeskRemoteCheckInNotice(guest, checkedIn, room.roomNumber());
        }

        return checkedIn;
    }
}
