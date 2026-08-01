package dev.portfolio.couponrush.domain.user.dto;

import dev.portfolio.couponrush.domain.user.entity.User;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class UserResponse {

    private final Long id;
    private final String email;
    private final String nickname;
    private final LocalDateTime createdAt;

    private UserResponse(Long id, String email, String nickname, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.nickname = nickname;
        this.createdAt = createdAt;
    }

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getCreatedAt()
        );
    }
}
