package com.coupon.concurrency.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
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
    private Instant issuedAt;

    @Builder
    public CouponIssue(User user, Coupon coupon) {
        this.user = user;
        this.coupon = coupon;
        this.status = CouponIssueStatus.ISSUED;
        this.issuedAt = Instant.now();
    }
}