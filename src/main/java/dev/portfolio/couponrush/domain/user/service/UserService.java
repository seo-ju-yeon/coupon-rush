package dev.portfolio.couponrush.domain.user.service;

import dev.portfolio.couponrush.common.exception.BusinessException;
import dev.portfolio.couponrush.common.exception.ErrorCode;
import dev.portfolio.couponrush.domain.user.dto.UserCreateRequest;
import dev.portfolio.couponrush.domain.user.dto.UserResponse;
import dev.portfolio.couponrush.domain.user.entity.User;
import dev.portfolio.couponrush.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    // 사용자 생성
    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        log.info("--- 사용자 생성 ---");
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = new User(request.getEmail(), request.getNickname());
        User savedUser = userRepository.save(user);

        return UserResponse.from(savedUser);
    }

    // 사용자 단건 조회
    public UserResponse getUser(Long userId) {
        log.info("--- 사용자 단건 조회 ---");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return UserResponse.from(user);
    }
}
