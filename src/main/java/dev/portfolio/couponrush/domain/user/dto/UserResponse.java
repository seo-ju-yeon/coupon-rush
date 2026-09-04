package dev.portfolio.couponrush.domain.user.dto;

import dev.portfolio.couponrush.domain.user.entity.User;
import dev.portfolio.couponrush.domain.user.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "사용자 응답")
// 사용자 정보를 API 응답 형태로 전달하는 DTO임
public class UserResponse {

    // 사용자의 식별자를 반환함
    @Schema(description = "사용자 식별자", example = "1")
    private final Long id;

    // 사용자의 이메일을 반환함
    @Schema(description = "사용자 이메일", example = "user@example.com")
    private final String email;

    // 사용자의 닉네임을 반환함
    @Schema(description = "사용자 닉네임", example = "couponUser")
    private final String nickname;

    @Schema(description = "사용자 권한", example = "USER")
    private final UserRole role;

    // 사용자의 생성 일시를 반환함
    @Schema(
            description = "사용자 생성 일시",
            example = "2026-09-04T10:30:00"
    )
    private final LocalDateTime createdAt;

    private UserResponse(Long id, String email, String nickname, UserRole role, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.nickname = nickname;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static UserResponse from(User user) {
        // User 엔티티를 API 응답용 DTO로 변환함
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
