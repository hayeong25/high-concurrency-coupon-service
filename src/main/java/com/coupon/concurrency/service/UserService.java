package com.coupon.concurrency.service;

import com.coupon.concurrency.entity.User;
import com.coupon.concurrency.repository.CouponIssueRepository;
import com.coupon.concurrency.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final CouponIssueRepository couponIssueRepository;

    /**
     * 새로운 사용자를 등록한다.
     * 이벤트 참여 시 호출되며, 순차적으로 userId가 발급된다.
     *
     * @return 생성된 사용자 ID
     */
    @Transactional
    public Long register() {
        User user = User.create();
        userRepository.save(user);
        log.info("사용자 등록 완료 - userId: {}", user.getId());
        return user.getId();
    }

    /**
     * 모든 사용자 데이터를 초기화한다.
     * 테스트 시작 전 데이터 정리 용도로 사용된다.
     * 쿠폰 발급 이력도 함께 삭제된다 (FK 제약조건).
     *
     * @return 삭제된 사용자 수
     */
    @Transactional
    public long reset() {
        long count = userRepository.count();
        log.info("사용자 데이터 초기화 시작 - 기존 사용자 수: {}", count);

        // FK 제약조건으로 인해 발급 이력 먼저 삭제
        couponIssueRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        log.info("사용자 데이터 초기화 완료");
        return count;
    }

    /**
     * 전체 사용자 수를 조회한다.
     *
     * @return 등록된 사용자 수
     */
    @Transactional(readOnly = true)
    public long count() {
        return userRepository.count();
    }
}