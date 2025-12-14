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
 * 쿠폰 발급 API 컨트롤러
 * Phase 1: 비관적 락과 낙관적 락을 사용한 동시성 제어
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /**
     * 비관적 락을 사용하여 쿠폰을 발급한다.
     *
     * @param request 쿠폰 발급 요청
     * @return 쿠폰 발급 응답
     */
    @PostMapping("/issue/pessimistic")
    public ResponseEntity<CouponIssueResponse> issueWithPessimisticLock(
            @Valid @RequestBody CouponIssueRequest request) {
        log.info("쿠폰 발급 요청 (비관적 락) - couponId: {}, userId: {}",
                request.couponId(), request.userId());

        CouponIssueResponse response = couponService.issueWithPessimisticLock(
                request.couponId(), request.userId());

        return ResponseEntity.ok(response);
    }

    /**
     * 낙관적 락을 사용하여 쿠폰을 발급한다.
     *
     * @param request 쿠폰 발급 요청
     * @return 쿠폰 발급 응답
     */
    @PostMapping("/issue/optimistic")
    public ResponseEntity<CouponIssueResponse> issueWithOptimisticLock(
            @Valid @RequestBody CouponIssueRequest request) {
        log.info("쿠폰 발급 요청 (낙관적 락) - couponId: {}, userId: {}",
                request.couponId(), request.userId());

        CouponIssueResponse response = couponService.issueWithOptimisticLock(
                request.couponId(), request.userId());

        return ResponseEntity.ok(response);
    }

    /**
     * 쿠폰의 남은 수량을 조회한다.
     *
     * @param couponId 조회할 쿠폰 ID
     * @return 남은 수량
     */
    @GetMapping("/{couponId}/remaining")
    public ResponseEntity<Integer> getRemainingQuantity(@PathVariable Long couponId) {
        log.debug("쿠폰 남은 수량 조회 - couponId: {}", couponId);

        int remainingQuantity = couponService.getRemainingQuantity(couponId);

        return ResponseEntity.ok(remainingQuantity);
    }

    /**
     * 쿠폰의 발급 건수를 조회한다.
     *
     * @param couponId 조회할 쿠폰 ID
     * @return 발급 건수
     */
    @GetMapping("/{couponId}/issued-count")
    public ResponseEntity<Long> getIssuedCount(@PathVariable Long couponId) {
        log.debug("쿠폰 발급 건수 조회 - couponId: {}", couponId);

        long issuedCount = couponService.getIssuedCount(couponId);

        return ResponseEntity.ok(issuedCount);
    }
}