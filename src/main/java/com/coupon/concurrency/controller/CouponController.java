package com.coupon.concurrency.controller;

import com.coupon.concurrency.dto.CouponIssueRequest;
import com.coupon.concurrency.dto.CouponIssueResponse;
import com.coupon.concurrency.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 쿠폰 관리 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/coupon")
@RequiredArgsConstructor
public class CouponController {
    private final CouponService couponService;

    /**
     * 쿠폰을 생성한다.
     * 기존의 모든 쿠폰 발급 이력과 쿠폰 데이터를 삭제하고,
     * 1,000개의 새로운 쿠폰(12자리 랜덤 쿠폰 번호)을 일괄 생성한다.
     *
     * @return 생성된 쿠폰 개수
     */
    @GetMapping("/create")
    public ResponseEntity<Integer> createCoupons() {
        log.info("쿠폰 생성 API 호출");
        return ResponseEntity.ok(couponService.createCoupons());
    }

    /**
     * 비관적 락을 사용하여 쿠폰을 발급한다.
     *
     * @param request 쿠폰 발급 요청 (userId)
     * @return 쿠폰 발급 응답
     */
    @PostMapping("/issue/pessimistic")
    public ResponseEntity<CouponIssueResponse> issueWithPessimisticLock(@Valid @RequestBody CouponIssueRequest request) {
        log.info("쿠폰 발급 요청 (비관적 락) - userId: {}", request.userId());
        CouponIssueResponse response = couponService.issueWithPessimisticLock(request.userId());
        return ResponseEntity.ok(response);
    }

    /**
     * Redis 분산 락을 사용하여 쿠폰을 발급한다.
     * Redisson의 RLock을 활용하여 분산 환경에서 동시성을 제어한다.
     * DB 락 대신 Redis 락을 사용하여 DB 부하를 줄인다.
     *
     * @param request 쿠폰 발급 요청 (userId)
     * @return 쿠폰 발급 응답
     */
    @PostMapping("/issue/redis")
    public ResponseEntity<CouponIssueResponse> issueWithRedisLock(@Valid @RequestBody CouponIssueRequest request) {
        log.info("쿠폰 발급 요청 (Redis 분산 락) - userId: {}", request.userId());
        CouponIssueResponse response = couponService.issueWithRedisLock(request.userId());
        return ResponseEntity.ok(response);
    }

    /**
     * 쿠폰의 남은 수량을 조회한다.
     *
     * @return 남은 수량
     */
    @GetMapping("/remaining")
    public ResponseEntity<Integer> getRemainingQuantity() {
        log.debug("쿠폰 남은 수량 조회");
        int remainingQuantity = couponService.getRemainingQuantity();
        return ResponseEntity.ok(remainingQuantity);
    }

    /**
     * 쿠폰의 발급 건수를 조회한다.
     *
     * @return 발급 건수
     */
    @GetMapping("/issued-count")
    public ResponseEntity<Long> getIssuedCount() {
        log.debug("쿠폰 발급 건수 조회");
        long issuedCount = couponService.getIssuedCount();
        return ResponseEntity.ok(issuedCount);
    }
}