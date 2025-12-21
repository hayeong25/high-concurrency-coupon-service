package com.coupon.concurrency.repository;

import com.coupon.concurrency.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    /**
     * 비관적 락을 사용하여 발급 가능한 첫 번째 쿠폰을 조회한다.
     * 아직 발급되지 않은 쿠폰 중 가장 작은 ID를 가진 쿠폰을 조회한다.
     *
     * @return 발급 가능한 쿠폰 엔티티
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.isIssued = false ORDER BY c.id ASC LIMIT 1")
    Optional<Coupon> findFirstAvailableWithPessimisticLock();

    /**
     * 발급 가능한 쿠폰의 잔여 수량을 조회한다.
     * 아직 발급되지 않은 쿠폰의 개수를 반환한다.
     *
     * @return 잔여 쿠폰 수량
     */
    @Query("SELECT COUNT(c) FROM Coupon c WHERE c.isIssued = false")
    int countAvailableCoupons();
}