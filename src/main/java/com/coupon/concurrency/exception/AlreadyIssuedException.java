package com.coupon.concurrency.exception;

/**
 * 이미 쿠폰을 발급받은 사용자가 중복 발급 시도 시 발생하는 예외
 */
public class AlreadyIssuedException extends RuntimeException {

    public AlreadyIssuedException() {
        super("이미 쿠폰을 발급받으셨습니다.");
    }

    public AlreadyIssuedException(String message) {
        super(message);
    }

    public AlreadyIssuedException(Long userId, Long couponId) {
        super("사용자(ID: " + userId + ")는 이미 쿠폰(ID: " + couponId + ")을 발급받으셨습니다.");
    }
}