package com.coupon.concurrency.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer discountAmount;

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    private Integer issuedQuantity;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Coupon(String name, Integer discountAmount, Integer totalQuantity,
                  LocalDateTime startTime, LocalDateTime endTime) {
        this.name = name;
        this.discountAmount = discountAmount;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0;
        this.startTime = startTime;
        this.endTime = endTime;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 발급 가능 여부 확인
     */
    public boolean isIssuable() {
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(startTime)
                && now.isBefore(endTime)
                && issuedQuantity < totalQuantity;
    }

    /**
     * 쿠폰 재고 소진 여부 확인
     */
    public boolean isSoldOut() {
        return issuedQuantity >= totalQuantity;
    }

    /**
     * 쿠폰 발급 처리 (수량 차감)
     *
     * @throws IllegalStateException 재고 부족 시
     */
    public void issue() {
        if (isSoldOut()) {
            throw new IllegalStateException("쿠폰이 모두 소진되었습니다.");
        }
        this.issuedQuantity++;
    }

    /**
     * 남은 쿠폰 수량 조회
     */
    public int getRemainingQuantity() {
        return totalQuantity - issuedQuantity;
    }
}