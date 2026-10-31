package com.cricauction.modules.notification.repository;

import com.cricauction.modules.notification.entity.DevicePushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DevicePushTokenRepository extends JpaRepository<DevicePushToken, Long> {

    Optional<DevicePushToken> findByDeviceToken(String deviceToken);

    List<DevicePushToken> findByUserIdAndIsActiveTrue(Long userId);
}
