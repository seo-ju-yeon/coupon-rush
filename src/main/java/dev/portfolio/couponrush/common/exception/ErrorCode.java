package dev.portfolio.couponrush.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    // 로그인 이메일 또는 비밀번호가 올바르지 않은 경우 사용함
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED,"이메일 또는 비밀번호가 올바르지 않습니다."),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "쿠폰을 찾을 수 없습니다."),
    INVALID_COUPON_PERIOD(HttpStatus.BAD_REQUEST, "쿠폰 발급 종료 일시는 시작 일시보다 늦어야 합니다."),
    COUPON_SOLD_OUT(HttpStatus.CONFLICT, "쿠폰 수량이 모두 소진되었습니다."),
    COUPON_NOT_OPEN(HttpStatus.CONFLICT, "현재 발급할 수 없는 쿠폰입니다."),
    DUPLICATE_COUPON_ISSUE(HttpStatus.CONFLICT, "이미 발급받은 쿠폰입니다."),
    COUPON_ISSUE_NOT_USABLE(HttpStatus.CONFLICT, "사용할 수 없는 쿠폰 발급 내역입니다."),
    COUPON_ISSUE_USER_MISMATCH(HttpStatus.FORBIDDEN, "쿠폰 발급 사용자와 주문 사용자가 일치하지 않습니다."),
    INVALID_ORDER_AMOUNT(HttpStatus.BAD_REQUEST, "주문 금액은 할인 금액보다 크거나 같아야 합니다."),
    COUPON_ISSUE_NOT_FOUND(HttpStatus.NOT_FOUND, "쿠폰 발급 내역을 찾을 수 없습니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
