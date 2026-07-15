package com.staydesk.repository;

import com.staydesk.model.Rate;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RateRepository extends ListCrudRepository<Rate, Integer> {

    @Query("SELECT * FROM rates WHERE rate_type = :rateType AND guest_count = :guestCount")
    Optional<Rate> findByRateTypeAndGuestCount(@Param("rateType") Rate.RateType rateType,
                                               @Param("guestCount") Integer guestCount);
}
