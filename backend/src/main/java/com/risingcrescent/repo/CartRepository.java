package com.risingcrescent.repo;

import com.risingcrescent.domain.entity.Cart;
import com.risingcrescent.domain.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByOwner(UserAccount owner);
}
