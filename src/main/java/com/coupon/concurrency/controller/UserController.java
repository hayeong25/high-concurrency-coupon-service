package com.coupon.concurrency.controller;

import com.coupon.concurrency.dto.UserRegisterResponse;
import com.coupon.concurrency.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * 사용자 관리 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    /**
     * 새로운 사용자를 등록한다.
     * 이벤트 참여 시 호출되며, 순차적으로 userId가 발급된다.
     *
     * @return 사용자 등록 응답 (userId, registeredAt)
     */
    @PostMapping("/register")
    public ResponseEntity<UserRegisterResponse> register() {
        log.info("사용자 등록 API 호출");
        Long userId = userService.register();
        UserRegisterResponse response = UserRegisterResponse.of(userId, Instant.now());
        return ResponseEntity.ok(response);
    }

    /**
     * 모든 사용자 데이터를 초기화한다.
     * 테스트 시작 전 데이터 정리 용도로 사용된다.
     *
     * @return 삭제된 사용자 수
     */
    @DeleteMapping("/reset")
    public ResponseEntity<Long> reset() {
        log.info("사용자 초기화 API 호출");
        long deletedCount = userService.reset();
        return ResponseEntity.ok(deletedCount);
    }

    /**
     * 전체 사용자 수를 조회한다.
     *
     * @return 등록된 사용자 수
     */
    @GetMapping("/count")
    public ResponseEntity<Long> count() {
        log.debug("사용자 수 조회");
        long count = userService.count();
        return ResponseEntity.ok(count);
    }
}