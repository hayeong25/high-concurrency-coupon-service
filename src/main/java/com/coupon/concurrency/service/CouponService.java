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

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {
    private static final int TOTAL_COUPON_COUNT = 1000;
    private static final int COUPON_CODE_LENGTH = 12;
    private static final String COUPON_CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String DEFAULT_COUPON_NAME = "10,000원 할인 쿠폰";

    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 쿠폰을 생성한다.
     * 기존의 모든 쿠폰 발급 이력과 쿠폰 데이터를 삭제하고,
     * 1,000개의 새로운 쿠폰을 일괄 생성한다.
     * 각 쿠폰은 12자리의 랜덤 쿠폰 번호(영문 대문자 + 숫자)를 가진다.
     *
     * @return 생성된 쿠폰 개수
     */
    @Transactional
    public int createCoupons() {
        log.info("쿠폰 생성 시작 - 기존 데이터 삭제 후 {}개 쿠폰 생성", TOTAL_COUPON_COUNT);

        // 기존 데이터 삭제 (발급 이력 먼저 삭제 후 쿠폰 삭제 - FK 제약조건)
        couponIssueRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();

        log.info("기존 데이터 삭제 완료");

        // 1,000개 쿠폰 생성
        List<Coupon> coupons = new ArrayList<>(TOTAL_COUPON_COUNT);
        for (int i = 0; i < TOTAL_COUPON_COUNT; i++) {
            Coupon coupon = Coupon.builder()
                    .couponCode(generateCouponCode())
                    .name(DEFAULT_COUPON_NAME)
                    .build();
            coupons.add(coupon);
        }

        couponRepository.saveAll(coupons);

        log.info("쿠폰 생성 완료 - 총 {}개 생성됨", TOTAL_COUPON_COUNT);

        return TOTAL_COUPON_COUNT;
    }

    /**
     * 12자리 랜덤 쿠폰 번호를 생성한다.
     * 영문 대문자(A-Z)와 숫자(0-9)로 구성된다.
     *
     * @return 12자리 랜덤 쿠폰 번호
     */
    private String generateCouponCode() {
        StringBuilder sb = new StringBuilder(COUPON_CODE_LENGTH);
        for (int i = 0; i < COUPON_CODE_LENGTH; i++) {
            int index = secureRandom.nextInt(COUPON_CODE_CHARACTERS.length());
            sb.append(COUPON_CODE_CHARACTERS.charAt(index));
        }
        return sb.toString();
    }

    /**
     * 비관적 락(Pessimistic Lock)을 사용하여 쿠폰을 발급한다.
     * DB 레벨에서 배타적 락을 획득하여 동시성을 제어한다.
     * 발급 가능한 쿠폰 중 가장 작은 ID를 가진 쿠폰을 선착순으로 발급한다.
     *
     * @param userId 발급받을 사용자 ID
     * @return 쿠폰 발급 응답
     * @throws UserNotFoundException  사용자를 찾을 수 없는 경우
     * @throws AlreadyIssuedException 이미 발급받은 경우
     * @throws CouponSoldOutException 쿠폰이 소진된 경우
     */
    @Transactional
    public CouponIssueResponse issueWithPessimisticLock(Long userId) {
        log.debug("비관적 락으로 쿠폰 발급 시도 - userId: {}", userId);

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 중복 발급 체크 (1인 1쿠폰 제한)
        if (couponIssueRepository.existsByUserId(userId)) {
            throw new AlreadyIssuedException(userId);
        }

        // 비관적 락으로 발급 가능한 첫 번째 쿠폰 조회
        Coupon coupon = couponRepository.findFirstAvailableWithPessimisticLock()
                .orElseThrow(CouponSoldOutException::new);

        // 쿠폰 발급 처리
        coupon.issue();

        // 발급 이력 저장
        CouponIssue couponIssue = CouponIssue.builder()
                .user(user)
                .coupon(coupon)
                .build();
        couponIssueRepository.save(couponIssue);

        log.info("쿠폰 발급 완료 (비관적 락) - userId: {}, couponId: {}, 잔여 수량: {}", userId, coupon.getId(), couponRepository.countAvailableCoupons());

        return CouponIssueResponse.from(couponIssue);
    }

    /**
     * 낙관적 락(Optimistic Lock)을 사용하여 쿠폰을 발급한다.
     * Version 컬럼을 통해 동시성 충돌을 감지하고, 충돌 시 예외가 발생한다.
     * 발급 가능한 쿠폰 중 가장 작은 ID를 가진 쿠폰을 선착순으로 발급한다.
     *
     * @param userId 발급받을 사용자 ID
     * @return 쿠폰 발급 응답
     * @throws UserNotFoundException  사용자를 찾을 수 없는 경우
     * @throws AlreadyIssuedException 이미 발급받은 경우
     * @throws CouponSoldOutException 쿠폰이 소진된 경우
     */
    @Transactional
    public CouponIssueResponse issueWithOptimisticLock(Long userId) {
        log.debug("낙관적 락으로 쿠폰 발급 시도 - userId: {}", userId);

        // 사용자 조회
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        // 중복 발급 체크 (1인 1쿠폰 제한)
        if (couponIssueRepository.existsByUserId(userId)) {
            throw new AlreadyIssuedException(userId);
        }

        // 낙관적 락으로 발급 가능한 첫 번째 쿠폰 조회
        Coupon coupon = couponRepository.findFirstAvailableWithOptimisticLock()
                .orElseThrow(CouponSoldOutException::new);

        // 쿠폰 발급 처리
        coupon.issue();

        // 발급 이력 저장
        CouponIssue couponIssue = CouponIssue.builder()
                .user(user)
                .coupon(coupon)
                .build();
        couponIssueRepository.save(couponIssue);

        log.info("쿠폰 발급 완료 (낙관적 락) - userId: {}, couponId: {}, 잔여 수량: {}", userId, coupon.getId(), couponRepository.countAvailableCoupons());

        return CouponIssueResponse.from(couponIssue);
    }

    /**
     * 발급 가능한 쿠폰의 잔여 수량을 조회한다.
     * 아직 발급되지 않은 쿠폰의 개수를 반환한다.
     *
     * @return 잔여 쿠폰 수량
     */
    @Transactional(readOnly = true)
    public int getRemainingQuantity() {
        return couponRepository.countAvailableCoupons();
    }

    /**
     * 총 발급 건수를 조회한다.
     *
     * @return 발급 건수
     */
    @Transactional(readOnly = true)
    public long getIssuedCount() {
        return couponIssueRepository.count();
    }
}