package com.coupon.concurrency.repository;

import com.coupon.concurrency.entity.CouponIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponIssueRepository extends JpaRepository<CouponIssue, Long> {

    /**
     * 특정 사용자가 특정 쿠폰을 이미 발급받았는지 확인한다.
     *
     * @param userId   사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급 여부
     */
    @Query("SELECT COUNT(ci) > 0 FROM CouponIssue ci WHERE ci.user.id = :userId AND ci.coupon.id = :couponId")
    boolean existsByUserIdAndCouponId(@Param("userId") Long userId, @Param("couponId") Long couponId);

    /**
     * 특정 사용자가 특정 쿠폰을 발급받은 내역을 조회한다.
     *
     * @param userId   사용자 ID
     * @param couponId 쿠폰 ID
     * @return 쿠폰 발급 내역
     */
    @Query("SELECT ci FROM CouponIssue ci WHERE ci.user.id = :userId AND ci.coupon.id = :couponId")
    Optional<CouponIssue> findByUserIdAndCouponId(@Param("userId") Long userId, @Param("couponId") Long couponId);

    /**
     * 특정 쿠폰의 발급 건수를 조회한다.
     *
     * @param couponId 쿠폰 ID
     * @return 발급 건수
     */
    @Query("SELECT COUNT(ci) FROM CouponIssue ci WHERE ci.coupon.id = :couponId")
    long countByCouponId(@Param("couponId") Long couponId);
}