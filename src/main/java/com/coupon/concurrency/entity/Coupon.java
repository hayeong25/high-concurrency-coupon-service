package com.coupon.concurrency.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "coupon",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_coupon_code", columnNames = "coupon_code")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_code", nullable = false, length = 12)
    private String couponCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "is_issued", nullable = false)
    private boolean isIssued;

    @Version
    private Long version;

    @Column(name = "create_time", nullable = false, updatable = false)
    private Instant createTime;

    @Column(name = "update_time")
    private Instant updateTime;

    @PrePersist
    protected void onCreate() {
        this.createTime = Instant.now();
    }

    @Builder
    public Coupon(String couponCode, String name) {
        this.couponCode = couponCode;
        this.name = name;
        this.isIssued = false;
    }

    public void issue() {
        this.isIssued = true;
        this.updateTime = Instant.now();
    }
}