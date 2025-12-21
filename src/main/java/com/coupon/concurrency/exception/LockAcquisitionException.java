package com.coupon.concurrency.exception;

/**
 * 분산 락 획득 실패 시 발생하는 예외
 */
public class LockAcquisitionException extends RuntimeException {

    public LockAcquisitionException() {
        super("락 획득에 실패했습니다. 잠시 후 다시 시도해주세요.");
    }

    public LockAcquisitionException(String message) {
        super(message);
    }
}