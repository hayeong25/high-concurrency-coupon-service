package com.coupon.concurrency.repository;

import com.coupon.concurrency.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * 비관적 락을 사용하여 쿠폰을 조회한다.
     * 다른 트랜잭션의 읽기/쓰기를 차단하여 동시성을 제어한다.
     *
     * @param id 조회할 쿠폰 ID
     * @return 쿠폰 엔티티
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdWithPessimisticLock(@Param("id") Long id);

    /**
     * 낙관적 락을 사용하여 쿠폰을 조회한다.
     * Version 컬럼을 통해 동시성 충돌을 감지한다.
     *
     * @param id 조회할 쿠폰 ID
     * @return 쿠폰 엔티티
     */
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdWithOptimisticLock(@Param("id") Long id);
}