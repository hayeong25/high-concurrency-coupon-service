package com.coupon.concurrency.repository;

import com.coupon.concurrency.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}