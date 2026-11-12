package com.cosmetics.ecommerce.repository;

import com.cosmetics.ecommerce.entity.User;
import com.cosmetics.ecommerce.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    long countByRole(Role role);
    Optional<User> findByReferralCodeIgnoreCase(String referralCode);
    boolean existsByReferralCode(String referralCode);
    Optional<User> findByGoogleId(String googleId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}