package com.coupon.concurrency.exception;

/**
 * 쿠폰 발급 기간이 아닐 때 발생하는 예외
 */
public class CouponNotAvailableException extends RuntimeException {

    public CouponNotAvailableException() {
        super("쿠폰 발급 기간이 아닙니다.");
    }

    public CouponNotAvailableException(String message) {
        super(message);
    }

    public CouponNotAvailableException(Long couponId) {
        super("쿠폰(ID: " + couponId + ")은 현재 발급 기간이 아닙니다.");
    }
}