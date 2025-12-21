package com.coupon.concurrency.service;

import com.coupon.concurrency.dto.CouponIssueResponse;
import com.coupon.concurrency.entity.Coupon;
import com.coupon.concurrency.entity.CouponIssue;
import com.coupon.concurrency.entity.User;
import com.coupon.concurrency.exception.CouponSoldOutException;
import com.coupon.concurrency.repository.CouponIssueRepository;
import com.coupon.concurrency.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 발급 트랜잭션 처리 서비스.
 * Redis 분산 락과 함께 사용되며, 트랜잭션 내에서 쿠폰 발급을 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponTransactionService {

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;

    /**
     * 트랜잭션 내에서 쿠폰 발급을 처리한다.
     * Redis 락 획득 후 호출되어 실제 DB 작업을 수행한다.
     *
     * @param user 발급받을 사용자
     * @return 쿠폰 발급 응답
     * @throws CouponSoldOutException 쿠폰이 소진된 경우
     */
    @Transactional
    public CouponIssueResponse issueInTransaction(User user) {
        // 락 없이 발급 가능한 첫 번째 쿠폰 조회
        Coupon coupon = couponRepository.findFirstAvailable()
                .orElseThrow(CouponSoldOutException::new);

        // 쿠폰 발급 처리
        coupon.issue();

        // 발급 이력 저장
        CouponIssue couponIssue = CouponIssue.builder()
                .user(user)
                .coupon(coupon)
                .build();
        couponIssueRepository.save(couponIssue);

        log.info("쿠폰 발급 완료 (Redis 락) - userId: {}, couponId: {}, 잔여 수량: {}",
                user.getId(), coupon.getId(), couponRepository.countAvailableCoupons());

        return CouponIssueResponse.from(couponIssue);
    }
}