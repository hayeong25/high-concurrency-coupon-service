package com.coupon.concurrency.exception;

/**
 * 쿠폰을 찾을 수 없을 때 발생하는 예외
 */
public class CouponNotFoundException extends RuntimeException {

    public CouponNotFoundException() {
        super("쿠폰을 찾을 수 없습니다.");
    }

    public CouponNotFoundException(String message) {
        super(message);
    }

    public CouponNotFoundException(Long couponId) {
        super("쿠폰(ID: " + couponId + ")을 찾을 수 없습니다.");
    }
}