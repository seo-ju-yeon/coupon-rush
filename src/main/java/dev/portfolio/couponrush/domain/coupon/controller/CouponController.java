package dev.portfolio.couponrush.domain.coupon.controller;

import dev.portfolio.couponrush.domain.coupon.dto.CouponCreateRequest;
import dev.portfolio.couponrush.domain.coupon.dto.CouponResponse;
import dev.portfolio.couponrush.domain.coupon.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(
        name = "쿠폰 API",
        description = "쿠폰 생성 및 조회 기능을 제공함"
)
@Log4j2
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    @Operation(
            summary = "쿠폰 생성",
            description = "쿠폰 정보와 발급 수량 및 기간을 입력받아 새로운 쿠폰을 생성함"
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse createCoupon(@Valid @RequestBody CouponCreateRequest request) {
        log.info("쿠폰 생성 요청: name={}", request.getName());

        return couponService.createCoupon(request);
    }

    @Operation(
            summary = "쿠폰 단건 조회",
            description = "쿠폰 ID로 쿠폰 정보를 조회함"
    )
    @GetMapping("/{couponId}")
    public CouponResponse getCoupon(@PathVariable Long couponId) {
        log.info("쿠폰 단건 조회 요청: couponId={}", couponId);

        return couponService.getCoupon(couponId);
    }

    @Operation(
            summary = "쿠폰 목록 조회",
            description = "등록된 전체 쿠폰 목록을 조회함"
    )
    @GetMapping
    public List<CouponResponse> getCoupons() {
        log.info("쿠폰 목록 조회 요청");

        return couponService.getCoupons();
    }
}
