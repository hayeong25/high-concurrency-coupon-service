package com.coupon.concurrency.entity;

import lombok.Getter;

@Getter
public enum CouponIssueStatus {
    ISSUED("발급됨"),
    USED("사용됨"),
    EXPIRED("만료됨");

    private final String description;

    CouponIssueStatus(String description) {
        this.description = description;
    }
}