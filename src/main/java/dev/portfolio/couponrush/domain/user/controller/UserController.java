package dev.portfolio.couponrush.domain.user.controller;

import dev.portfolio.couponrush.domain.user.dto.UserCreateRequest;
import dev.portfolio.couponrush.domain.user.dto.UserResponse;
import dev.portfolio.couponrush.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "사용자 API",
        description = "사용자 생성 및 조회 기능을 제공함"
)
@Log4j2
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "사용자 생성",
            description = "이메일과 닉네임을 입력받아 새로운 사용자를 생성함"
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody UserCreateRequest request) {
        log.info("사용자 생성 요청: email={}, nickname={}",
                request.getEmail(),
                request.getNickname());
        return userService.createUser(request);
    }

    @Operation(
            summary = "사용자 단건 조회",
            description = "사용자 ID로 사용자 정보를 조회함"
    )
    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable Long userId) {
        log.info("사용자 단건 조회 요청: userId={}", userId);
        return userService.getUser(userId);
    }
}
