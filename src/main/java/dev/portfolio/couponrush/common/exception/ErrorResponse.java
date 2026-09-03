package dev.portfolio.couponrush.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "API 오류 응답")
public class ErrorResponse {

    @Schema(description = "오류를 식별하는 코드", example = "USER_NOT_FOUND")
    private final String code;

    @Schema(description = "오류 원인에 대한 설명", example = "사용자를 찾을 수 없습니다.")
    private final String message;

    private ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.name(),
                errorCode.getMessage()
        );
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(
                errorCode.name(),
                message
        );
    }
}
