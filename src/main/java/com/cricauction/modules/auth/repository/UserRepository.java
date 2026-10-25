package com.cricauction.modules.auth.repository;

import com.cricauction.modules.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByMobile(String mobile);

    @Query("SELECT u FROM User u WHERE u.email = :identifier OR u.mobile = :identifier")
    Optional<User> findByIdentifier(@Param("identifier") String identifier);

    Optional<User> findByGoogleId(String googleId);

    @Query("SELECT u FROM User u WHERE (u.googleId IS NOT NULL AND u.googleId = :googleId) OR (u.email IS NOT NULL AND u.email = :email)")
    Optional<User> findByGoogleIdOrEmail(@Param("googleId") String googleId, @Param("email") String email);

    @Query("SELECT u.email, u.mobile FROM User u WHERE u.email = :email OR u.mobile = :mobile")
    List<Object[]> findExistingIdentifiers(@Param("email") String email, @Param("mobile") String mobile);

    boolean existsByGoogleId(String googleId);

    boolean existsByEmail(String email);

    boolean existsByMobile(String mobile);
}
