package com.coupon.concurrency.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant createTime;

    private User(Instant createTime) {
        this.createTime = createTime;
    }

    /**
     * 새로운 사용자를 생성한다.
     * 생성 시점의 시간이 자동으로 기록된다.
     *
     * @return 생성된 사용자
     */
    public static User create() {
        return new User(Instant.now());
    }
}