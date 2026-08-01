package dev.portfolio.couponrush.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    // 서비스 계층에서 의도적으로 발생시키는 비즈니스 예외

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
