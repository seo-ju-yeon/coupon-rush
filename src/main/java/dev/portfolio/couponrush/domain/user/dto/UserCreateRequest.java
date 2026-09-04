package dev.portfolio.couponrush.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "사용자 생성 요청")
// 사용자 생성 API의 요청 데이터를 담는 DTO임
public class UserCreateRequest {

    // 사용자 이메일의 필수 입력과 형식을 검증함
    @Schema(
            description = "사용자 이메일",
            example = "user@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 255, message = "이메일은 255자를 초과할 수 없습니다.")
    private String email;

    // 사용자 닉네임의 필수 입력과 길이를 검증함
    @Schema(
            description = "사용자 닉네임",
            example = "couponUser",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(max = 50, message = "닉네임은 50자를 초과할 수 없습니다.")
    private String nickname;

    // 사용자 비밀번호의 필수 입력과 길이를 검증함
    @Schema(
            description = "사용자 비밀번호",
            example = "password123!",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(
            min = 8,
            max = 64,
            message = "비밀번호는 8자 이상 64자 이하여야 합니다."
    )
    private String password;
}
