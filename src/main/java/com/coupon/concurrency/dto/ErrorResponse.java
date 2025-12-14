package com.coupon.concurrency.dto;

import java.time.LocalDateTime;

/**
 * 에러 응답 DTO
 *
 * @param code      에러 코드
 * @param message   에러 메시지
 * @param timestamp 발생 시각
 */
public record ErrorResponse(
        String code,
        String message,
        LocalDateTime timestamp
) {
    /**
     * 에러 응답을 생성한다.
     *
     * @param code    에러 코드
     * @param message 에러 메시지
     * @return 에러 응답 DTO
     */
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, LocalDateTime.now());
    }
}