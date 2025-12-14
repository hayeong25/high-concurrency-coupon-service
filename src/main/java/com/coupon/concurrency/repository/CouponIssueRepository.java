package com.coupon.concurrency.repository;

import com.coupon.concurrency.entity.CouponIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponIssueRepository extends JpaRepository<CouponIssue, Long> {
    /**
     * 특정 사용자가 쿠폰을 이미 발급받았는지 확인한다.
     * 선착순 이벤트에서 1인 1쿠폰 제한을 위해 사용한다.
     *
     * @param userId 사용자 ID
     * @return 발급 여부
     */
    @Query("SELECT COUNT(ci) > 0 FROM CouponIssue ci WHERE ci.user.id = :userId")
    boolean existsByUserId(@Param("userId") Long userId);
}