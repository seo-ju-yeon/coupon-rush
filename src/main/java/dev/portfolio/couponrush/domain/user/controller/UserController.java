package dev.portfolio.couponrush.domain.user.controller;

import dev.portfolio.couponrush.domain.user.dto.UserCreateRequest;
import dev.portfolio.couponrush.domain.user.dto.UserResponse;
import dev.portfolio.couponrush.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Log4j2
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody UserCreateRequest request) {
        log.info("사용자 생성 요청: email={}, nickname={}", request.getEmail(), request.getNickname());
        return userService.createUser(request);
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable Long userId) {
        log.info("사용자 단건 조회 요청: userId={}", userId);
        return userService.getUser(userId);
    }
}
