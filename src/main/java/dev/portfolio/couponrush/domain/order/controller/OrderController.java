package dev.portfolio.couponrush.domain.order.controller;

import dev.portfolio.couponrush.domain.order.dto.OrderCreateRequest;
import dev.portfolio.couponrush.domain.order.dto.OrderResponse;
import dev.portfolio.couponrush.domain.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "주문 API",
        description = "발급받은 쿠폰을 사용하여 주문을 생성하는 기능을 제공함"
)
@Log4j2
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @Operation(
            summary = "주문 생성",
            description = "로그인한 사용자가 발급받은 쿠폰으로 주문을 생성함"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "주문 생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청값 또는 주문 금액 검증 실패"),
            @ApiResponse(responseCode = "403", description = "쿠폰 발급 사용자와 주문 사용자가 일치하지 않음"),
            @ApiResponse(responseCode = "404", description = "사용자 또는 쿠폰 발급 내역을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "사용할 수 없는 쿠폰 발급 내역")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OrderCreateRequest request
    ) {
        // JWT의 subject에서 로그인한 사용자 ID를 가져옴
        Long userId = Long.valueOf(jwt.getSubject());

        log.info(
                "주문 생성 API 요청: userId={}, couponIssueId={}, originalAmount={}",
                userId,
                request.getCouponIssueId(),
                request.getOriginalAmount()
        );

        return orderService.createOrder(
                userId,
                request
        );
    }
}
