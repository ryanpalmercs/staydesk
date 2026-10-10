package com.staydesk.repository;

import com.staydesk.model.RemoteCheckInToken;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RemoteCheckInTokenRepository extends ListCrudRepository<RemoteCheckInToken, Integer> {

    Optional<RemoteCheckInToken> findByTokenHash(String tokenHash);

    boolean existsByReservationId(int reservationId);

    @Modifying
    @Query("UPDATE remote_checkin_tokens SET used_at = now() WHERE id = :id")
    void markUsed(@Param("id") Integer id);
}
