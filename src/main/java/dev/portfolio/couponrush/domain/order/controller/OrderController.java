package dev.portfolio.couponrush.domain.order.controller;

import dev.portfolio.couponrush.domain.order.dto.OrderCreateRequest;
import dev.portfolio.couponrush.domain.order.dto.OrderResponse;
import dev.portfolio.couponrush.domain.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
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
            description = "사용자와 발급 쿠폰 정보를 받아 할인이 적용된 주문을 생성함"
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(
            @Valid @RequestBody OrderCreateRequest request
    ) {
        log.info(
                "주문 생성 API 요청: userId={}, couponIssueId={}, originalAmount={}",
                request.getUserId(),
                request.getCouponIssueId(),
                request.getOriginalAmount()
        );

        return orderService.createOrder(request);
    }
}
