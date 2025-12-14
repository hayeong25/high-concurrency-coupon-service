package com.coupon.concurrency.service;

import com.coupon.concurrency.dto.CouponIssueResponse;
import com.coupon.concurrency.entity.Coupon;
import com.coupon.concurrency.entity.CouponIssue;
import com.coupon.concurrency.entity.User;
import com.coupon.concurrency.exception.*;
import com.coupon.concurrency.repository.CouponIssueRepository;
import com.coupon.concurrency.repository.CouponRepository;
import com.coupon.concurrency.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 발급 서비스
 * Phase 1: 비관적 락과 낙관적 락을 사용한 동시성 제어
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final UserRepository userRepository;

    /**
     * 비관적 락(Pessimistic Lock)을 사용하여 쿠폰을 발급한다.
     * DB 레벨에서 배타적 락을 획득하여 동시성을 제어한다.
     *
     * @param couponId 발급할 쿠폰 ID
     * @param userId   발급받을 사용자 ID
     * @return 쿠폰 발급 응답
     * @throws CouponNotFoundException     쿠폰을 찾을 수 없는 경우
     * @throws UserNotFoundException       사용자를 찾을 수 없는 경우
     * @throws AlreadyIssuedException      이미 발급받은 경우
     * @throws CouponSoldOutException      쿠폰이 소진된 경우
     * @throws CouponNotAvailableException 발급 기간이 아닌 경우
     */
    @Transactional
    public CouponIssueResponse issueWithPessimisticLock(Long couponId, Long userId) {
        log.debug("비관적 락으로 쿠폰 발급 시도 - couponId: {}, userId: {}", couponId, userId);

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 중복 발급 체크
        if (couponIssueRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new AlreadyIssuedException(userId, couponId);
        }

        // 비관적 락으로 쿠폰 조회
        Coupon coupon = couponRepository.findByIdWithPessimisticLock(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));

        // 발급 가능 여부 확인
        if (!coupon.isIssuable()) {
            if (coupon.isSoldOut()) {
                throw new CouponSoldOutException(couponId);
            }
            throw new CouponNotAvailableException(couponId);
        }

        // 쿠폰 발급 처리
        coupon.issue();

        // 발급 이력 저장
        CouponIssue couponIssue = CouponIssue.builder()
                .user(user)
                .coupon(coupon)
                .build();
        couponIssueRepository.save(couponIssue);

        log.info("쿠폰 발급 완료 (비관적 락) - couponId: {}, userId: {}, 남은 수량: {}",
                couponId, userId, coupon.getRemainingQuantity());

        return CouponIssueResponse.from(couponIssue);
    }

    /**
     * 낙관적 락(Optimistic Lock)을 사용하여 쿠폰을 발급한다.
     * Version 컬럼을 통해 동시성 충돌을 감지하고, 충돌 시 예외가 발생한다.
     *
     * @param couponId 발급할 쿠폰 ID
     * @param userId   발급받을 사용자 ID
     * @return 쿠폰 발급 응답
     * @throws CouponNotFoundException     쿠폰을 찾을 수 없는 경우
     * @throws UserNotFoundException       사용자를 찾을 수 없는 경우
     * @throws AlreadyIssuedException      이미 발급받은 경우
     * @throws CouponSoldOutException      쿠폰이 소진된 경우
     * @throws CouponNotAvailableException 발급 기간이 아닌 경우
     */
    @Transactional
    public CouponIssueResponse issueWithOptimisticLock(Long couponId, Long userId) {
        log.debug("낙관적 락으로 쿠폰 발급 시도 - couponId: {}, userId: {}", couponId, userId);

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 중복 발급 체크
        if (couponIssueRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new AlreadyIssuedException(userId, couponId);
        }

        // 낙관적 락으로 쿠폰 조회
        Coupon coupon = couponRepository.findByIdWithOptimisticLock(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));

        // 발급 가능 여부 확인
        if (!coupon.isIssuable()) {
            if (coupon.isSoldOut()) {
                throw new CouponSoldOutException(couponId);
            }
            throw new CouponNotAvailableException(couponId);
        }

        // 쿠폰 발급 처리
        coupon.issue();

        // 발급 이력 저장
        CouponIssue couponIssue = CouponIssue.builder()
                .user(user)
                .coupon(coupon)
                .build();
        couponIssueRepository.save(couponIssue);

        log.info("쿠폰 발급 완료 (낙관적 락) - couponId: {}, userId: {}, 남은 수량: {}",
                couponId, userId, coupon.getRemainingQuantity());

        return CouponIssueResponse.from(couponIssue);
    }

    /**
     * 쿠폰의 남은 수량을 조회한다.
     *
     * @param couponId 조회할 쿠폰 ID
     * @return 남은 수량
     * @throws CouponNotFoundException 쿠폰을 찾을 수 없는 경우
     */
    @Transactional(readOnly = true)
    public int getRemainingQuantity(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
        return coupon.getRemainingQuantity();
    }

    /**
     * 특정 쿠폰의 총 발급 건수를 조회한다.
     *
     * @param couponId 조회할 쿠폰 ID
     * @return 발급 건수
     */
    @Transactional(readOnly = true)
    public long getIssuedCount(Long couponId) {
        return couponIssueRepository.countByCouponId(couponId);
    }
}