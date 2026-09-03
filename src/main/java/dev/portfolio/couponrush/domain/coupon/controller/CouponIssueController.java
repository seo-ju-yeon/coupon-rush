package dev.portfolio.couponrush.domain.coupon.controller;

import dev.portfolio.couponrush.domain.coupon.dto.CouponIssueCreateRequest;
import dev.portfolio.couponrush.domain.coupon.dto.CouponIssueResponse;
import dev.portfolio.couponrush.domain.coupon.service.CouponIssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "쿠폰 발급 성공"),
            @ApiResponse(responseCode = "400", description = "요청값 또는 쿠폰 발급 기간 검증 실패"),
            @ApiResponse(responseCode = "404", description = "쿠폰 또는 사용자를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "발급 불가, 수량 소진 또는 중복 발급")
    })
    @PostMapping("/{couponId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public CouponIssueResponse issueResponse(
            @Parameter(description = "발급할 쿠폰 ID", example = "1")
            @PathVariable Long couponId,
            @Valid @RequestBody CouponIssueCreateRequest request
    ) {
        log.info("쿠폰 발급 API 요청: couponId={}, userId={}",
                couponId,
                request.getUserId());

        return couponIssueService.issueCoupon(couponId, request);
    }
}
