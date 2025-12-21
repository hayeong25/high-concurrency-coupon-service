package com.coupon.concurrency.exception;

import com.coupon.concurrency.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리 핸들러
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 쿠폰 소진 예외 처리
     *
     * @param e 쿠폰 소진 예외
     * @return 에러 응답
     */
    @ExceptionHandler(CouponSoldOutException.class)
    public ResponseEntity<ErrorResponse> handleCouponSoldOut(CouponSoldOutException e) {
        log.warn("쿠폰 소진: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("COUPON_SOLD_OUT", e.getMessage()));
    }

    /**
     * 중복 발급 예외 처리
     *
     * @param e 중복 발급 예외
     * @return 에러 응답
     */
    @ExceptionHandler(AlreadyIssuedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyIssued(AlreadyIssuedException e) {
        log.warn("중복 발급 시도: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("ALREADY_ISSUED", e.getMessage()));
    }

    /**
     * 사용자 미존재 예외 처리
     *
     * @param e 사용자 미존재 예외
     * @return 에러 응답
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException e) {
        log.warn("사용자 없음: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("USER_NOT_FOUND", e.getMessage()));
    }

    /**
     * 분산 락 획득 실패 예외 처리
     *
     * @param e 락 획득 실패 예외
     * @return 에러 응답
     */
    @ExceptionHandler(LockAcquisitionException.class)
    public ResponseEntity<ErrorResponse> handleLockAcquisition(LockAcquisitionException e) {
        log.warn("락 획득 실패: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("LOCK_ACQUISITION_FAILED", e.getMessage()));
    }

    /**
     * 유효성 검증 실패 예외 처리
     *
     * @param e 유효성 검증 실패 예외
     * @return 에러 응답
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("유효성 검증에 실패했습니다.");
        log.warn("유효성 검증 실패: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", message));
    }

    /**
     * 기타 예외 처리
     *
     * @param e 예외
     * @return 에러 응답
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("예상치 못한 오류 발생", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "서버 오류가 발생했습니다."));
    }
}