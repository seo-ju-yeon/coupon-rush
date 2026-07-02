package dev.portfolio.couponrush.domain.user.repository;

import dev.portfolio.couponrush.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
