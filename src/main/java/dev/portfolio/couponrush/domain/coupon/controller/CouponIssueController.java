package dev.portfolio.couponrush.domain.coupon.controller;

import dev.portfolio.couponrush.domain.coupon.dto.CouponIssueCreateRequest;
import dev.portfolio.couponrush.domain.coupon.dto.CouponIssueResponse;
import dev.portfolio.couponrush.domain.coupon.service.CouponIssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "쿠폰 발급 API",
        description = "사용자에게 쿠폰을 발급하는 기능을 제공함"
)
@Log4j2
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/coupons")
public class CouponIssueController {

    private final CouponIssueService couponIssueService;

    @Operation(
            summary = "쿠폰 발급",
            description = "쿠폰 ID와 사용자 ID를 받아 해당 사용자에게 쿠폰을 발급함"
    )
    @PostMapping("/{couponId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public CouponIssueResponse issueResponse(
            @PathVariable Long couponId,
            @Valid @RequestBody CouponIssueCreateRequest request
    ) {
        log.info("쿠폰 발급 API 요청: couponId={}, userId={}",
                couponId,
                request.getUserId());

        return couponIssueService.issueCoupon(couponId, request);
    }
}
