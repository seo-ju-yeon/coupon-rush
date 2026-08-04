package dev.portfolio.couponrush.domain.order.controller;

import dev.portfolio.couponrush.domain.order.dto.OrderCreateRequest;
import dev.portfolio.couponrush.domain.order.dto.OrderResponse;
import dev.portfolio.couponrush.domain.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Log4j2
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

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
