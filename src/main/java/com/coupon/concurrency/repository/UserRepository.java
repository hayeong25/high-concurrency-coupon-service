package com.coupon.concurrency.repository;

import com.coupon.concurrency.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 이메일로 사용자를 조회한다.
     *
     * @param email 사용자 이메일
     * @return 사용자 엔티티
     */
    Optional<User> findByEmail(String email);

    /**
     * 이메일 존재 여부를 확인한다.
     *
     * @param email 사용자 이메일
     * @return 존재 여부
     */
    boolean existsByEmail(String email);
}