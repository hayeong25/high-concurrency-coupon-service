package com.coupon.concurrency.dto;

import com.coupon.concurrency.entity.CouponIssue;
import com.coupon.concurrency.entity.CouponIssueStatus;

import java.time.Instant;

/**
 * 쿠폰 발급 응답 DTO
 *
 * @param couponIssueId  발급된 쿠폰 발급 ID
 * @param couponId       쿠폰 ID
 * @param couponName     쿠폰명
 * @param userId         사용자 ID
 * @param status         발급 상태
 * @param issuedAt       발급 일시
 */
public record CouponIssueResponse(
        Long couponIssueId,
        Long couponId,
        String couponName,
        Long userId,
        CouponIssueStatus status,
        Instant issuedAt
) {
    /**
     * CouponIssue 엔티티로부터 응답 DTO를 생성한다.
     *
     * @param couponIssue 쿠폰 발급 엔티티
     * @return 쿠폰 발급 응답 DTO
     */
    public static CouponIssueResponse from(CouponIssue couponIssue) {
        return new CouponIssueResponse(
                couponIssue.getId(),
                couponIssue.getCoupon().getId(),
                couponIssue.getCoupon().getName(),
                couponIssue.getUser().getId(),
                couponIssue.getStatus(),
                couponIssue.getIssuedAt()
        );
    }
}