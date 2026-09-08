package dev.portfolio.couponrush.domain.auth.service;

import dev.portfolio.couponrush.common.exception.BusinessException;
import dev.portfolio.couponrush.common.exception.ErrorCode;
import dev.portfolio.couponrush.common.security.JwtTokenProvider;
import dev.portfolio.couponrush.domain.auth.dto.LoginRequest;
import dev.portfolio.couponrush.domain.auth.dto.TokenResponse;
import dev.portfolio.couponrush.domain.user.entity.User;
import dev.portfolio.couponrush.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
// 로그인 정보를 검증하고 인증에 성공한 사용자에게 JWT를 발급함
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public TokenResponse login(LoginRequest request) {
        // 입력받은 이메일로 사용자를 조회함
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.INVALID_CREDENTIALS)
                );

        // 입력한 평문 비밀번호와 DB에 저장된 BCrypt 해시를 비교함
        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash()
        )) {
            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS
            );
        }

        // 인증에 성공한 사용자의 정보를 담은 Access Token을 생성함
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        return TokenResponse.of(
                accessToken,
                jwtTokenProvider.getAccessTokenExpirationSeconds()
        );
    }
}
