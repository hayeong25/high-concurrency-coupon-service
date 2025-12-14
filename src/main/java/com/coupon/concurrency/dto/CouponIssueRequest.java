package com.coupon.concurrency.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 쿠폰 발급 요청 DTO
 *
 * @param userId 쿠폰 발급 받을 사용자 ID
 */
public record CouponIssueRequest(
        @NotNull(message = "사용자 ID는 필수입니다.")
        Long userId
) {
}