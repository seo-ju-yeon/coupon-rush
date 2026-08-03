package dev.portfolio.couponrush.domain.user.dto;

import dev.portfolio.couponrush.domain.user.entity.User;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
// 사용자 정보를 API 응답 형태로 전달하는 DTO임
public class UserResponse {

    // 사용자의 식별자를 반환함
    private final Long id;
    // 사용자의 이메일을 반환함
    private final String email;
    // 사용자의 닉네임을 반환함
    private final String nickname;
    // 사용자의 생성 일시를 반환함
    private final LocalDateTime createdAt;

    private UserResponse(Long id, String email, String nickname, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.nickname = nickname;
        this.createdAt = createdAt;
    }

    public static UserResponse from(User user) {
        // User 엔티티를 API 응답용 DTO로 변환함
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getCreatedAt()
        );
    }
}
