package com.cricauction.modules.auth.repository;

import com.cricauction.modules.auth.entity.OtpVerification;
import com.cricauction.modules.auth.entity.OtpVerificationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, OtpVerificationId> {

    Optional<OtpVerification> findByIdentifierAndPurpose(String identifier, String purpose);

    void deleteByIdentifierAndPurpose(String identifier, String purpose);

    @Query("SELECT o FROM OtpVerification o WHERE (o.identifier = :mobile OR o.mobile = :mobile) AND o.purpose = :purpose")
    Optional<OtpVerification> findByMobileAndPurpose(@Param("mobile") String mobile, @Param("purpose") String purpose);

    @Modifying
    @Query("DELETE FROM OtpVerification o WHERE (o.identifier = :mobile OR o.mobile = :mobile) AND o.purpose = :purpose")
    void deleteByMobileAndPurpose(@Param("mobile") String mobile, @Param("purpose") String purpose);

    @Modifying
    @Query("DELETE FROM OtpVerification o WHERE o.expiresAt < :now")
    void deleteExpiredOtps(@Param("now") Instant now);
}
