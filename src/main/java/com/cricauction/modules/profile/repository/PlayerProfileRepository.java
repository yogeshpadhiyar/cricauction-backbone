package com.cricauction.modules.profile.repository;

import com.cricauction.modules.profile.entity.PlayerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlayerProfileRepository extends JpaRepository<PlayerProfile, Long> {
    Optional<PlayerProfile> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
}
