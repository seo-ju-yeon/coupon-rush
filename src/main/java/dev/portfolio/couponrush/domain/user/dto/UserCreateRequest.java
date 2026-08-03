package dev.portfolio.couponrush.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
// 사용자 생성 API의 요청 데이터를 담는 DTO임
public class UserCreateRequest {

    // 사용자 이메일의 필수 입력과 형식을 검증함
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 255, message = "이메일은 255자를 초과할 수 없습니다.")
    private String email;

    // 사용자 닉네임의 필수 입력과 길이를 검증함
    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(max = 50, message = "닉네임은 50자를 초과할 수 없습니다.")
    private String nickname;
}
