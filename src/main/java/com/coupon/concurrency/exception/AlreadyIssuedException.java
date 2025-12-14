package com.coupon.concurrency.exception;

/**
 * 이미 쿠폰을 발급받은 사용자가 중복 발급 시도 시 발생하는 예외
 */
public class AlreadyIssuedException extends RuntimeException {
    /**
     * 특정 사용자가 이미 쿠폰을 발급받았을 때 발생하는 예외
     *
     * @param userId 사용자 ID
     */
    public AlreadyIssuedException(Long userId) {
        super("사용자(ID: " + userId + ")는 이미 쿠폰을 발급받으셨습니다.");
    }
}