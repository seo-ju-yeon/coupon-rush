package dev.portfolio.couponrush.domain.coupon.controller;

import dev.portfolio.couponrush.domain.coupon.dto.CouponCreateRequest;
import dev.portfolio.couponrush.domain.coupon.dto.CouponResponse;
import dev.portfolio.couponrush.domain.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Log4j2
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse createCoupon(@Valid @RequestBody CouponCreateRequest request) {
        log.info("쿠폰 생성 요청: name={}", request.getName());

        return couponService.createCoupon(request);
    }

    @GetMapping("/{couponId}")
    public CouponResponse getCoupon(@PathVariable Long couponId) {
        log.info("쿠폰 단건 조회 요청: couponId={}", couponId);

        return couponService.getCoupon(couponId);
    }

    @GetMapping
    public List<CouponResponse> getCoupons() {
        log.info("쿠폰 목록 조회 요청");

        return couponService.getCoupons();
    }
}
