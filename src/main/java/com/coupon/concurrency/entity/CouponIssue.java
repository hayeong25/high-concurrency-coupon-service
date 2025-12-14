package com.coupon.concurrency.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_issue",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_coupon_issues_user_coupon",
                        columnNames = {"user_id", "coupon_id"}
                )
        },
        indexes = {
                @Index(name = "idx_coupon_issue_user_id", columnList = "user_id"),
                @Index(name = "idx_coupon_issue_coupon_id", columnList = "coupon_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponIssueStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    private LocalDateTime usedAt;

    @Builder
    public CouponIssue(User user, Coupon coupon) {
        this.user = user;
        this.coupon = coupon;
        this.status = CouponIssueStatus.ISSUED;
        this.issuedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 사용 처리
     *
     * @throws IllegalStateException 이미 사용되었거나 만료된 경우
     */
    public void use() {
        if (this.status != CouponIssueStatus.ISSUED) {
            throw new IllegalStateException("사용 가능한 상태가 아닙니다. 현재 상태: " + status.getDescription());
        }
        this.status = CouponIssueStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 만료 처리
     */
    public void expire() {
        if (this.status == CouponIssueStatus.USED) {
            throw new IllegalStateException("이미 사용된 쿠폰은 만료 처리할 수 없습니다.");
        }
        this.status = CouponIssueStatus.EXPIRED;
    }

    /**
     * 쿠폰 사용 가능 여부 확인
     */
    public boolean isUsable() {
        return this.status == CouponIssueStatus.ISSUED;
    }
}