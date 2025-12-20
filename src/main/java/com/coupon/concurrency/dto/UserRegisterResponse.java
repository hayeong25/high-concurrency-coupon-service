package com.coupon.concurrency.dto;

import java.time.Instant;

/**
 * 사용자 등록 응답 DTO
 *
 * @param userId       발급된 사용자 ID
 * @param registeredAt 등록 일시
 */
public record UserRegisterResponse(
        Long userId,
        Instant registeredAt
) {
    /**
     * 사용자 ID와 등록 시간으로 응답 DTO를 생성한다.
     *
     * @param userId       사용자 ID
     * @param registeredAt 등록 일시
     * @return 사용자 등록 응답 DTO
     */
    public static UserRegisterResponse of(Long userId, Instant registeredAt) {
        return new UserRegisterResponse(userId, registeredAt);
    }
}