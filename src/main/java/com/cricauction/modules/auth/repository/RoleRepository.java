package com.cricauction.modules.auth.repository;

import com.cricauction.modules.auth.entity.Role;
import com.cricauction.modules.auth.entity.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {
    Optional<Role> findByName(UserRole name);
}
