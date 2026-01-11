# 선착순 쿠폰 발급 시스템 - Architecture Decision Record

> 대규모 트래픽(TPS 10,000+) 환경에서 선착순 쿠폰 발급 시스템의 동시성 이슈를 단계적으로 해결하며 아키텍처를 진화시킨 과정을 기록합니다.

---

## 1. 프로젝트 개요

### 1.1 배경

현재 사내에서 선불시스템을 개발하고 있으며, 오픈 기념 프로모션으로 선착순 쿠폰 발급 이벤트를 기획하고 있습니다.

선착순 쿠폰 발급 이벤트는 고객 유입과 구매 전환에 효과적인 마케팅 전략이지만, 이벤트 시작과 동시에 수만 명이 동시 접속하는 상황은 시스템에 큰 도전을 안겨줍니다.

실제 이벤트를 진행하기 전에 대규모 트래픽 처리 방안을 미리 검증하고자 이 프로젝트를 시작하게 되었습니다.

### 1.2 비즈니스 요구사항

| 항목 | 내용 |
|:---|:---|
| 이벤트 시간 | 매일 오전 10시 |
| 발급 수량 | 선착순 1,000명 |
| 쿠폰 금액 | 10,000원 할인 |
| 종료 조건 | 수량 소진 시 즉시 마감 |
| 목표 TPS | 10,000+ |

### 1.3 예상 트래픽 시나리오

```
[트래픽 패턴]

     ▲ 요청량
     │
10000│        ██
     │       ████
     │      ██████
     │     ████████
     │    ██████████
     │___██████████████_______________▶ 시간
         10:00  10:01  10:02
         (이벤트 시작)
```

- **피크 시간**: 이벤트 시작 후 수 초 ~ 수십 초
- **예상 동시 접속자**: 수만 명
- **트래픽 집중도**: 전체 트래픽의 80% 이상이 1분 이내에 발생

### 1.4 해결해야 할 기술적 과제

| 과제 | 설명 | 영향 |
|:---|:---|:---|
| 높은 TPS 달성 | 초당 10,000건 이상의 요청 처리 | 사용자 경험, 이벤트 성공 |
| 낮은 응답 시간 | 밀리초 단위의 빠른 응답 | 사용자 이탈 방지 |
| 시스템 안정성 | 트래픽 폭주에도 서버 다운 방지 | 서비스 신뢰도 |
| 데이터 정합성 | 정확히 1,000개만 발급 (Overselling 방지) | 비즈니스 손실 방지 |

---

## 2. 기술 스택

### 2.1 공통 인프라 구성

| 구성 요소 | 스펙 |
|:---|:---|
| **Application Server** | Local (Windows) |
| **Database** | MySQL 8.0 (Docker) |
| **Redis** | Redis 7 Alpine (Docker) |
| **JDK** | Java 21 |
| **Framework** | Spring Boot 3.4.1 |
| **Redis 클라이언트** | Redisson 3.40.2 |
| **부하 테스트 도구** | nGrinder 3.9.1 |

### 2.2 부하 테스트 도구: nGrinder

| 비교 항목 | nGrinder | JMeter | Gatling |
|:---|:---|:---|:---|
| 스크립트 언어 | Groovy/Jython | XML/GUI | Scala |
| 분산 테스트 | 내장 지원 | 복잡한 설정 필요 | 유료 |
| 실시간 모니터링 | 우수 | 보통 | 우수 |
| 국내 레퍼런스 | 풍부 (네이버 개발) | 풍부 | 적음 |

**nGrinder를 선택한 이유:**

1. **분산 부하 테스트 내장 지원**
    - Agent를 추가하는 것만으로 쉽게 부하를 증가시킬 수 있습니다.
    - 대규모 트래픽을 시뮬레이션하기에 적합합니다.

2. **실시간 TPS/Latency 모니터링**
    - 테스트 중 실시간으로 성능 지표를 확인할 수 있습니다.
    - 병목 지점을 빠르게 파악할 수 있습니다.

3. **국내 기술 블로그 레퍼런스**
    - 네이버에서 개발한 오픈소스입니다.
    - 대용량 트래픽 테스트 사례가 풍부합니다.

### 2.3 고성능 데이터 저장소: Redis

| 비교 항목 | MySQL | Redis |
|:---|:---|:---|
| 저장 방식 | 디스크 | 인메모리 |
| 읽기 성능 | ~10,000 QPS | ~100,000+ QPS |
| 쓰기 성능 | ~5,000 QPS | ~80,000+ QPS |
| 지연 시간 | 수 ms | 수십 μs ~ 수백 μs |

---

## 3. 아키텍처 진화 과정

```
Phase 1: [Client] → [Server] → [MySQL (DB Lock)]
         └─ 병목: DB Connection 고갈, 낮은 TPS

Phase 2: [Client] → [Server] → [Redis Lock] → [MySQL]
         └─ 개선: DB 락 제거, TPS 향상
         └─ 남은 과제: 불필요한 트래픽이 서버에 도달

Phase 3: [Client] → [Server] → [Redis Counter] → [MySQL]
         └─ 개선: Fast Fail 도입
         └─ 남은 과제: DB 접근 병목 여전

Phase 4: 성능 최적화 및 최대 TPS 도출 (예정)
```

---

## 4. Phase 1: 비관적 락 (Pessimistic Lock)

### 4.1 목표

가장 단순한 구현으로 **기준 성능(Baseline)**을 측정합니다.

### 4.2 아키텍처

```
[Client] → [Spring Boot Server] → [MySQL (Pessimistic Lock)]
```

### 4.3 구현 방식

JPA를 활용한 기본적인 쿠폰 발급 로직입니다.

```java
@Service
@RequiredArgsConstructor
public class CouponService {

    @Transactional
    public CouponIssueResponse issueWithPessimisticLock(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (couponIssueRepository.existsByUserId(userId)) {
            throw new AlreadyIssuedException(userId);
        }

        // 비관적 락으로 발급 가능한 첫 번째 쿠폰 조회
        Coupon coupon = couponRepository.findFirstAvailableWithPessimisticLock()
                .orElseThrow(CouponSoldOutException::new);

        coupon.issue();
        couponIssueRepository.save(new CouponIssue(user, coupon));

        return CouponIssueResponse.from(couponIssue);
    }
}
```

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM Coupon c WHERE c.issued = false ORDER BY c.id ASC LIMIT 1")
Optional<Coupon> findFirstAvailableWithPessimisticLock();
```

**API 엔드포인트**: `POST /api/v1/coupon/issue/pessimistic`

### 4.4 테스트 환경

| 항목 | 설정값 |
|:---|:---|
| **VUser** | 600명 |
| **Duration** | 약 86초 |
| **Agent** | 1대 |
| **동시성 제어** | MySQL 비관적 락 (PESSIMISTIC_WRITE) |
| **쿠폰 수량** | 1,000개 |

### 4.5 테스트 결과

#### 핵심 지표

| 지표 | 측정값 | 목표 | 달성 여부 |
|:---|:---:|:---:|:---:|
| **평균 TPS** | 24.6 | 10,000+ | ❌ |
| **최대 TPS** | 47.5 | - | - |
| **최소 TPS** | 7.0 | - | - |
| **평균 응답시간** | 18,942ms | < 100ms | ❌ |
| **최소 응답시간** | 4,892ms | - | - |
| **최대 응답시간** | 28,870ms | - | - |
| **에러율** | 0% | 0% | ✅ |
| **총 처리 건수** | 2,063건 | - | - |

#### 데이터 정합성 검증

| 항목 | 값 | 검증 |
|:---|:---:|:---:|
| **발급된 쿠폰 수** | 1,000개 | ✅ |
| **남은 쿠폰 수** | 0개 | ✅ |
| **Overselling 발생** | 없음 | ✅ |

### 4.6 발견된 병목

| 병목 지점 | 원인 | 영향 |
|:---|:---|:---|
| **DB Connection Pool 고갈** | 동시 요청 폭주로 커넥션 부족 | 요청 대기 |
| **락 대기 시간** | 비관적 락으로 순차 처리 | 응답 지연 |
| **DB CPU 과부하** | 모든 요청이 DB 거침 | 성능 저하 |

```
[병목 지점 시각화]

Request → [Server] → [DB Lock 대기] → [DB 처리] → Response
                          ↑
                    병목 발생 지점
```

### 4.7 Phase 1 결론

RDBMS만으로는 대용량 트래픽을 감당하기 어렵습니다. **DB 접근을 최소화**하고, **더 빠른 처리 계층**을 앞단에 배치할 필요가 있습니다.

---

## 5. Phase 2: Redis 분산 락 (Distributed Lock)

### 5.1 목표

DB 병목을 해소하기 위해 **락 처리를 인메모리 기반의 Redis로 이동**합니다.

### 5.2 아키텍처

```
[Client] → [Spring Boot Server] → [Redis (Distributed Lock)] → [MySQL]
```

### 5.3 Phase 1 대비 개선 포인트

| Phase 1 병목 | Phase 2 해결 방안 |
|:---|:---|
| DB Connection 고갈 | Redis 인메모리 락으로 DB 접근 최소화 |
| 락 대기 시간 | Redis의 빠른 응답 속도 활용 |
| DB CPU 과부하 | 트래픽 분산으로 DB 부하 감소 |

### 5.4 구현 방식

Redisson의 분산 락을 활용했습니다.

```java
@Service
@RequiredArgsConstructor
public class CouponService {

    private final RedissonClient redissonClient;
    private static final String COUPON_LOCK_KEY = "coupon:lock";
    private static final long LOCK_WAIT_TIME = 30L;
    private static final long LOCK_LEASE_TIME = 30L;

    public CouponIssueResponse issueWithRedisLock(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (couponIssueRepository.existsByUserId(userId)) {
            throw new AlreadyIssuedException(userId);
        }

        RLock lock = redissonClient.getLock(COUPON_LOCK_KEY);

        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (!acquired) {
                throw new LockAcquisitionException();
            }

            return couponTransactionService.issueInTransaction(user);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("락 획득 중 인터럽트가 발생했습니다.");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

**API 엔드포인트**: `POST /api/v1/coupon/issue/redis`

### 5.5 Redis 락 설정

| 항목 | 설정값 | 설명 |
|:---|:---:|:---|
| **Lock Key** | `coupon:lock` | 단일 글로벌 락 |
| **Wait Time** | 30초 | 락 획득 대기 최대 시간 |
| **Lease Time** | 30초 | 락 보유 최대 시간 |

### 5.6 Redisson 선택 이유

```
[Lettuce vs Redisson]

Lettuce (스핀락):
while (!tryLock()) {
    Thread.sleep(100);  // 지속적인 Redis 요청 (비효율)
}

Redisson (Pub/Sub):
subscribe("lock-channel");
wait();  // 이벤트 기반 대기 (효율적)
```

### 5.7 테스트 환경

| 항목 | 설정값 |
|:---|:---|
| **VUser** | 1,500명 |
| **Duration** | 약 70초 |
| **Agent** | 1대 |
| **동시성 제어** | Redis 분산 락 (Redisson RLock) |
| **쿠폰 수량** | 1,000개 |

### 5.8 테스트 결과

#### 핵심 지표

| 지표 | 측정값 | 목표 | 달성 여부 |
|:---|:---:|:---:|:---:|
| **평균 TPS** | 39.3 | 10,000+ | ❌ |
| **최대 TPS** | 60.0 | - | - |
| **최소 TPS** | 8.5 | - | - |
| **평균 응답시간** | 30,120ms | < 100ms | ❌ |
| **최소 응답시간** | 7,244ms | - | - |
| **최대 응답시간** | 40,492ms | - | - |
| **에러율** | 0% | 0% | ✅ |
| **총 처리 건수** | 2,832건 | - | - |

#### 데이터 정합성 검증

| 항목 | 값 | 검증 |
|:---|:---:|:---:|
| **등록된 사용자 수** | 3,000명 | ✅ |
| **발급된 쿠폰 수** | 1,000개 | ✅ |
| **남은 쿠폰 수** | 0개 | ✅ |
| **Overselling 발생** | 없음 | ✅ |

### 5.9 Phase 1 vs Phase 2 비교

| 지표 | Phase 1 (DB Lock) | Phase 2 (Redis Lock) | 변화 |
|:---|:---:|:---:|:---:|
| **VUser** | 600 | 1,500 | +150% |
| **평균 TPS** | 24.6 | 39.3 | **+59.8%** |
| **평균 응답시간** | 18,942ms | 30,120ms | +59.0% |
| **에러율** | 0% | 0% | 동일 |
| **총 처리량** | 2,063건 | 2,832건 | **+37.3%** |

### 5.10 병목 분석

| 병목 지점 | 원인 | 영향 |
|:---|:---|:---|
| **단일 글로벌 락** | 모든 요청이 하나의 락 경쟁 | 순차 처리 |
| **락 대기열 누적** | 1,500 VUser 동시 대기 | 응답시간 30~40초 |
| **직렬화 처리** | 한 번에 1개 요청만 처리 | TPS 제한 |

```
[요청 흐름]

[요청 1] ─┐
[요청 2] ─┤
[요청 3] ─┼─▶ [Redis Lock 획득 대기] ─▶ [순차 처리] ─▶ [DB 저장] ─▶ [응답]
  ...     │           ↑
[요청 N] ─┘      Pub/Sub 기반 대기
```

### 5.11 Phase 2 결론

Redis 분산 락으로 **DB 병목은 해소**되었지만, 쿠폰이 소진된 후에도 모든 요청이 서버까지 도달하여 처리됩니다. **불필요한 트래픽을 사전에 차단하는 메커니즘**이 필요합니다.

---

## 6. Phase 3: Redis 유량 제어 (Rate Limiting)

### 6.1 목표

**처리 불가능한 요청을 최대한 빨리 거절(Fast Fail)**하여 시스템 부하를 최소화합니다.

### 6.2 아키텍처

```
[Client] → [Server] → [Redis (Atomic Counter)] → [MySQL]
                              ↓
                     (count > 1000: Fast Fail)
```

### 6.3 Phase 2 대비 개선 포인트

| Phase 2 문제 | Phase 3 해결 방안 |
|:---|:---|
| 모든 요청이 락 경쟁 | 원자적 카운터로 락 없이 처리 |
| 재고 소진 후에도 처리 시도 | 즉시 거절 (Fast Fail) |
| 서버 리소스 낭비 | 불필요한 요청 조기 차단 |

### 6.4 구현 방식

Redisson RAtomicLong을 활용한 원자적 카운터입니다.

```java
@Service
@RequiredArgsConstructor
public class CouponService {

    private final RedissonClient redissonClient;
    private static final int TOTAL_COUPON_COUNT = 1000;
    private static final String COUPON_COUNT_KEY = "coupon:count";

    public CouponIssueResponse issueWithRateLimiting(Long userId) {
        // 1. 사용자 검증
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 2. 중복 발급 체크
        if (couponIssueRepository.existsByUserId(userId)) {
            throw new AlreadyIssuedException(userId);
        }

        // 3. Redis 원자적 카운터로 선착순 체크 (Fast Fail)
        RAtomicLong counter = redissonClient.getAtomicLong(COUPON_COUNT_KEY);
        long currentCount = counter.incrementAndGet();

        if (currentCount > TOTAL_COUPON_COUNT) {
            // 선착순 실패 - 카운터 롤백 후 즉시 거절
            counter.decrementAndGet();
            throw new CouponSoldOutException();
        }

        // 4. 선착순 성공 - 실제 쿠폰 발급 처리
        return couponTransactionService.issueInTransaction(user);
    }
}
```

**API 엔드포인트**: `POST /api/v1/coupon/issue/ratelimit`

### 6.5 Rate Limiting 설정

| 항목 | 설정값 | 설명 |
|:---|:---:|:---|
| **Counter Key** | `coupon:count` | Redis 원자적 카운터 |
| **Max Count** | 1,000 | 선착순 제한 수량 |
| **Fast Fail** | 즉시 거절 | 1,001번째 이후 요청 |

### 6.6 테스트 환경

| 항목 | 설정값 |
|:---|:---|
| **VUser** | 600명 |
| **Duration** | 약 73초 |
| **Agent** | 1대 |
| **동시성 제어** | Redis 원자적 카운터 (RAtomicLong) |
| **쿠폰 수량** | 1,000개 |

### 6.7 테스트 결과

#### 핵심 지표

| 지표 | 측정값 | 목표 | 달성 여부 |
|:---|:---:|:---:|:---:|
| **평균 TPS** | 26.3 | 10,000+ | ❌ |
| **최대 TPS** | 83.5 | - | - |
| **최소 TPS** | 2.5 | - | - |
| **평균 응답시간** | 20,811ms | < 100ms | ❌ |
| **최소 응답시간** | 7,057ms | - | - |
| **최대 응답시간** | 31,315ms | - | - |
| **에러율** | 0% | 0% | ✅ |
| **총 처리 건수** | 1,882건 | - | - |

#### 데이터 정합성 검증

| 항목 | 값 | 검증 |
|:---|:---:|:---:|
| **발급된 쿠폰 수** | 1,000개 | ✅ |
| **남은 쿠폰 수** | 0개 | ✅ |
| **Overselling 발생** | 없음 | ✅ |

### 6.8 Phase 비교 (동일 VUser 600 기준)

| 지표 | Phase 1 (DB Lock) | Phase 3 (Rate Limit) | 변화 |
|:---|:---:|:---:|:---:|
| **평균 TPS** | 24.6 | 26.3 | **+6.9%** |
| **평균 응답시간** | 18,942ms | 20,811ms | +9.9% |
| **에러율** | 0% | 0% | 동일 |
| **Fast Fail** | 없음 | 있음 | **개선** |

### 6.9 병목 분석

```
[현재 처리 흐름]

[요청] → [사용자 검증 (DB)] → [중복 체크 (DB)] → [Redis Counter] → [쿠폰 발급 (DB)]
              ↑                     ↑                                    ↑
          병목 지점 1           병목 지점 2                            병목 지점 3
```

| 병목 지점 | 원인 | 영향 |
|:---|:---|:---|
| **DB 접근 병목** | 사용자 검증, 중복 체크가 Redis 이전에 실행 | Fast Fail 효과 제한 |
| **트랜잭션 직렬화** | 동시 DB 쓰기 시 락 경합 | TPS 제한 |
| **Fast Fail 효과 제한** | 쿠폰 소진 전에는 모든 요청이 DB 접근 | 초기 응답시간 높음 |

### 6.10 기대 vs 실제 결과 분석

```
[기대]
- Fast Fail로 인한 TPS 대폭 향상 (10,000+)
- 응답시간 < 100ms

[실제]
- TPS: 26.3 (Phase 1 대비 소폭 향상)
- 응답시간: 20,811ms

[원인]
1. existsByUserId() DB 조회가 Redis 카운터 이전에 실행
2. 선착순 1,000명은 여전히 DB 트랜잭션 필요
3. Fast Fail은 쿠폰 소진 후에만 효과 발휘
```

### 6.11 Phase 3 결론

Redis 원자적 카운터로 **수량 제어는 성공**했지만, **DB 접근 병목이 여전히 존재**합니다.

진정한 Fast Fail을 구현하려면:
1. 중복 발급 체크를 Redis로 이동 (Redis SET 활용)
2. 사용자 검증 순서 변경 (Redis 카운터 체크 후 DB 접근)
3. 비동기 처리 도입 (Kafka 등)

---

## 7. 전체 Phase 비교

### 7.1 성능 지표 비교

| 항목 | Phase 1 (DB Lock) | Phase 2 (Redis Lock) | Phase 3 (Rate Limit) |
|:---|:---:|:---:|:---:|
| **VUser** | 600 | 1,500 | 600 |
| **평균 TPS** | 24.6 | 39.3 | 26.3 |
| **최대 TPS** | 47.5 | 60.0 | 83.5 |
| **평균 응답시간** | 18,942ms | 30,120ms | 20,811ms |
| **총 처리량** | 2,063건 | 2,832건 | 1,882건 |
| **에러율** | 0% | 0% | 0% |
| **데이터 정합성** | 100% | 100% | 100% |

### 7.2 아키텍처 특성 비교

| 항목 | Phase 1 | Phase 2 | Phase 3 |
|:---|:---|:---|:---|
| **동시성 제어** | DB 비관적 락 | Redis 분산 락 | Redis 원자적 카운터 |
| **분산 환경** | 불가 | 가능 | 가능 |
| **Fast Fail** | 없음 | 없음 | 있음 |
| **락 오버헤드** | 높음 | 중간 | 없음 |

### 7.3 단계별 성과 요약

| 단계 | 주요 개선 | 효과 |
|:---|:---|:---|
| Phase 1 → 2 | Redis 분산 락 도입 | DB 병목 해소, TPS 59.8% 향상 |
| Phase 2 → 3 | 트래픽 제어 (Fast Fail) | 락 오버헤드 제거 |
| Phase 3 → 4 | DB 접근 최소화 (예정) | 진정한 Fast Fail 구현 |

---

## 8. 핵심 의사결정 요약

| 결정 사항 | 근거 |
|:---|:---|
| MySQL → Redis 도입 | 인메모리 기반 고성능, 초당 수십만 건 처리 가능 |
| 분산 락 → 원자적 카운터 | 락 오버헤드 제거, 더 높은 병렬성 |
| Redisson 선택 | Pub/Sub 기반 효율적 락, 대용량 트래픽에 적합 |
| nGrinder 선택 | 분산 부하 테스트로 실제 트래픽 시뮬레이션 |

---

## 9. 향후 계획

### 9.1 Phase 4: 성능 최적화

```
현재 문제: DB 접근 병목 (1인 1쿠폰 검증 + 쿠폰 발급)

개선 방향:
1. 1인 1쿠폰 검증을 Redis로 이동 (SET 자료구조)
2. 쿠폰 발급을 비동기 처리 (Kafka/RabbitMQ)
3. Redis에서 즉시 응답 후 백그라운드로 DB 저장
4. Connection Pool 최적화 (HikariCP 튜닝)
```

### 9.2 추가 성능 테스트

| 테스트 유형 | 목적 | 방법 |
|:---|:---|:---|
| **Ramp-up** | 한계점 도출 | 부하를 점진적으로 증가시켜 성능 저하 시점 확인 |
| **Stress** | 시스템 한계 확인 | 예상 최대치 이상의 부하를 가해 동작 확인 |
| **Spike** | 순간 폭주 대응 | 갑작스러운 트래픽 급증 시 동작 확인 |
| **Endurance** | 장시간 안정성 | 일정 부하를 장시간 유지하여 메모리 누수 확인 |

### 9.3 모니터링 및 관측성

- **Prometheus + Grafana**: 실시간 TPS, Latency 대시보드
- **Pinpoint APM**: 병목 구간 시각화, 트랜잭션 추적
- **AWS CloudWatch**: 인프라 레벨 모니터링

---

## 10. 결론

이 프로젝트에서는 선착순 쿠폰 발급 시스템을 구축하면서 대용량 트래픽을 처리하기 위해 다양한 전략을 시도했습니다.

RDBMS 기반의 단순한 구현에서 시작해 Redis를 활용한 고성능 아키텍처로 진화시키면서, **"어떻게 하면 더 많은 트래픽을 안정적으로 처리할 수 있을까?"**를 끊임없이 고민했습니다.

**핵심 인사이트:**
1. **병목 지점을 앞단으로 이동**: DB보다 Redis, Redis보다 앞단에서 처리할수록 TPS가 향상됩니다.
2. **Fast Fail 전략**: 처리 불가능한 요청은 최대한 빨리 거절하는 것이 시스템 전체 처리량을 높입니다.
3. **계층별 역할 분리**: 트래픽 제어(Redis) → 비즈니스 로직(Server) → 데이터 저장(DB)
4. **정량적 측정의 중요성**: TPS, Latency, Fail Rate 등 구체적인 수치로 개선 효과를 증명해야 합니다.

---

## 참고 자료

- [Redisson GitHub](https://github.com/redisson/redisson)
- [nGrinder GitHub](https://github.com/naver/ngrinder)