package com.coupon.concurrency.exception;

/**
 * 쿠폰 재고 소진 시 발생하는 예외
 */
public class CouponSoldOutException extends RuntimeException {
    public CouponSoldOutException() {
        super("쿠폰이 모두 소진되었습니다.");
    }
}