package com.coupon.concurrency.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 쿠폰 발급 요청 DTO
 *
 * @param couponId 발급받을 쿠폰 ID
 * @param userId   발급받을 사용자 ID
 */
public record CouponIssueRequest(
        @NotNull(message = "쿠폰 ID는 필수입니다.")
        Long couponId,

        @NotNull(message = "사용자 ID는 필수입니다.")
        Long userId
) {
}