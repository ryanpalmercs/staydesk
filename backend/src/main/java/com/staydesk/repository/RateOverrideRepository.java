package com.staydesk.repository;

import com.staydesk.model.RateOverride;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RateOverrideRepository extends ListCrudRepository<RateOverride, Integer> {

    // end_date is exclusive, matching reservations.check_in_date/check_out_date - end_date is the
    // day pricing returns to normal, so it isn't itself covered by the override.
    @Query("SELECT * FROM rate_overrides WHERE rate_type = :rateType AND guest_count = :guestCount " +
           "AND start_date <= :date AND end_date > :date")
    Optional<RateOverride> findActiveOverride(@Param("rateType") String rateType,
                                              @Param("guestCount") int guestCount, @Param("date") LocalDate date);

    @Query("SELECT * FROM rate_overrides WHERE rate_type = :rateType AND guest_count = :guestCount " +
           "AND start_date < :endDate AND end_date > :startDate AND (:excludeId IS NULL OR id != :excludeId)")
    List<RateOverride> findOverlapping(@Param("rateType") String rateType, @Param("guestCount") int guestCount,
                                       @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                       @Param("excludeId") Integer excludeId);
}
