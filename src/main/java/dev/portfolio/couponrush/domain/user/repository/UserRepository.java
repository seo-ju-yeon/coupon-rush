package dev.portfolio.couponrush.domain.user.repository;

import dev.portfolio.couponrush.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 이메일 중복 여부를 확인
    boolean existsByEmail(String email);

    // 로그인 요청의 이메일로 사용자를 조회함
    Optional<User> findByEmail(String email);
}
