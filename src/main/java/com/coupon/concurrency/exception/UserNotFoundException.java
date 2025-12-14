package com.coupon.concurrency.exception;

/**
 * 사용자를 찾을 수 없을 때 발생하는 예외
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long userId) {
        super("사용자(ID: " + userId + ")를 찾을 수 없습니다.");
    }
}