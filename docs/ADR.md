# 선착순 쿠폰 발급 시스템의 대용량 트래픽 처리 전략

---

## 시작하게 된 계기

현재 사내에서 선불시스템을 개발하고 있으며, 내년 오픈 예정입니다.

오픈하게 되면 기념 이벤트 프로모션을 진행할까 논의 중인데요,
선불시스템에서 사용 가능한 할인 쿠폰을 선착순으로 발급받아 사용할 수 있도록 하면 좋겠다는 의견이 나왔습니다.

그런데 선착순 쿠폰 발급 이벤트는 고객 유입과 구매 전환에 효과적인 마케팅 전략이지만,
이벤트 시작과 동시에 수만 명이 동시 접속하는 상황은 시스템에 큰 도전을 안겨줍니다.

순간적으로 몰리는 대량의 트래픽을 어떻게 안정적으로 처리할 수 있을까?
사용자에게 빠른 응답을 제공하면서도 정확한 수량만 발급하려면 어떻게 해야 할까?

이런 고민 끝에, 실제 이벤트를 진행하기 전에 대규모 트래픽 처리 방안을 미리 검증해보면 어떨까 하는 생각에 이 프로젝트를 시작하게 되었습니다.

이번 글에서는 선착순 쿠폰 발급 시스템을 구축하면서 대용량 트래픽을 처리하기 위해 시도한 다양한 전략과
그 과정에서 얻은 인사이트를 공유하고자 합니다.

단순한 RDBMS 기반 구현에서 시작해 Redis를 활용한 고성능 아키텍처로 진화시키며,
각 단계별 TPS 향상과 응답 시간 개선 결과를 소개합니다.
<br>

---

## 프로젝트 개요

### 비즈니스 요구사항

구축하려는 쿠폰 발급 시스템의 요구사항은 다음과 같습니다.

- 매일 오전 10시에 쿠폰 발급 버튼 활성화
- 선착순 **1,000명**에게만 10,000원 할인 쿠폰 발급
- 수량 소진 시 이벤트 즉시 마감
- **목표 TPS: 10,000+**
  <br>

### 예상 트래픽 시나리오

선착순 이벤트의 특성상 트래픽이 특정 시점에 집중됩니다.

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

이러한 **스파이크성 트래픽**을 안정적으로 처리하는 것이 이 프로젝트의 핵심 과제입니다.
<br>

### 해결해야 할 기술적 과제

| 과제            | 설명                              | 영향             |
|---------------|---------------------------------|----------------|
| **높은 TPS 달성** | 초당 10,000건 이상의 요청 처리            | 사용자 경험, 이벤트 성공 |
| **낮은 응답 시간**  | 밀리초 단위의 빠른 응답                   | 사용자 이탈 방지      |
| **시스템 안정성**   | 트래픽 폭주에도 서버 다운 방지               | 서비스 신뢰도        |
| **데이터 정합성**   | 정확히 1,000개만 발급 (Overselling 방지) | 비즈니스 손실 방지     |

<br>

---

## 기술 스택 선정

### 부하 테스트 도구: nGrinder

대용량 트래픽 처리 능력을 검증하기 위해서는 실제 서비스 환경과 유사한 부하를 생성할 수 있는 도구가 필요했습니다.

여러 도구를 검토한 결과, nGrinder를 선택했습니다.

| 비교 항목    | nGrinder      | JMeter    | Gatling |
|----------|---------------|-----------|---------|
| 스크립트 언어  | Groovy/Jython | XML/GUI   | Scala   |
| 분산 테스트   | 내장 지원         | 복잡한 설정 필요 | 유료      |
| 실시간 모니터링 | 우수            | 보통        | 우수      |
| 국내 레퍼런스  | 풍부 (네이버 개발)   | 풍부        | 적음      |

<br>

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
      <br>

### 고성능 데이터 저장소: Redis

대용량 트래픽을 처리하기 위해 Redis를 도입했습니다.

```
[RDBMS vs Redis 성능 비교]

RDBMS (MySQL):
- 디스크 기반 I/O
- 초당 수천 건 처리
- 트랜잭션 오버헤드 존재

Redis:
- 인메모리 기반
- 초당 수십만 건 처리
- 단순 연산에 최적화
```

<br>

| 비교 항목 | MySQL       | Redis         |
|-------|-------------|---------------|
| 저장 방식 | 디스크         | 인메모리          |
| 읽기 성능 | ~10,000 QPS | ~100,000+ QPS |
| 쓰기 성능 | ~5,000 QPS  | ~80,000+ QPS  |
| 지연 시간 | 수 ms        | 수십 μs ~ 수백 μs |

대용량 트래픽 상황에서 모든 요청을 RDBMS로 처리하면 병목이 발생합니다.

Redis를 앞단에 배치하여 트래픽을 흡수하고, 실제 DB 접근은 최소화하는 전략을 채택했습니다.
<br>

---

## Phase 1: RDBMS 기반 구현 (Baseline)

### 목표

먼저 가장 단순한 구현으로 **기준 성능(Baseline)**을 측정했습니다.

이후 Phase에서 얼마나 성능이 개선되었는지 비교하기 위한 기준점입니다.
<br>

### 아키텍처

```
[Client] → [Spring Boot Server] → [MySQL]
```

<br>

### 구현 방식

JPA를 활용한 기본적인 쿠폰 발급 로직입니다.

```java

@Service
@RequiredArgsConstructor
public class CouponService {

    @Transactional
    public void issueCoupon(Long couponId, Long userId) {
        Coupon coupon = couponRepository.findByIdWithLock(couponId);

        if (coupon.getQuantity() > 0) {
            coupon.decrease();
            issuedCouponRepository.save(new IssuedCoupon(couponId, userId));
        }
    }
}
```

데이터 정합성을 위해 비관적 락을 적용했습니다.

```java

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM Coupon c WHERE c.id = :id")
Coupon findByIdWithLock(@Param("id") Long id);
```

<br>

### 부하 테스트 결과

**[테스트 환경]**

- Application Server: AWS EC2 t3.medium
- Database: AWS RDS MySQL 8.x
- 동시 사용자: 1,000명
- 총 요청: 10,000건
  <br>

**[테스트 결과 - 업데이트 예정]**

| 지표              | 측정값 |   목표    | 달성 여부 |
|-----------------|:---:|:-------:|:-----:|
| **TPS**         |  -  | 10,000  |   -   |
| **Avg Latency** |  -  | < 100ms |   -   |
| **P99 Latency** |  -  | < 500ms |   -   |
| **Fail Rate**   |  -  |   0%    |   -   |

<br>

### 발견된 병목 지점

테스트 결과, 여러 병목 지점을 발견했습니다.
<br>

**1. DB Connection Pool 고갈**

```
HikariCP - Connection is not available, request timed out after 30000ms
```

동시 요청이 증가하면 모든 커넥션이 락 대기 상태에 빠지고,
새로운 요청은 커넥션을 할당받지 못했습니다.
<br>

**2. 락 대기로 인한 응답 지연**

비관적 락은 한 번에 하나의 트랜잭션만 처리할 수 있습니다.
요청이 직렬화(Serialization)되면서 응답 시간이 급격히 증가했습니다.
<br>

**3. DB CPU 과부하**

모든 요청이 DB를 거치면서 DB 서버의 CPU 사용률이 급등했습니다.

```
[병목 지점 시각화]

Request → [Server] → [DB Lock 대기] → [DB 처리] → Response
                          ↑
                    병목 발생 지점
```

<br>

### Phase 1 결론

RDBMS만으로는 대용량 트래픽을 감당하기 어렵습니다.

**DB 접근을 최소화**하고, **더 빠른 처리 계층**을 앞단에 배치할 필요가 있었습니다.
<br>

---

## Phase 2: Redis 분산 락 도입

### 목표

DB 병목을 해소하기 위해 **락 처리를 인메모리 기반의 Redis로 이동**합니다.

DB는 최종 데이터 저장에만 사용하고, 동시성 제어는 Redis가 담당하도록 역할을 분리합니다.
<br>

### 아키텍처

```
[Client] → [Spring Boot Server] → [Redis (Lock)] → [MySQL]
```

<br>

### Phase 1 대비 개선 포인트

| Phase 1 병목       | Phase 2 해결 방안            |
|------------------|--------------------------|
| DB Connection 고갈 | Redis 인메모리 락으로 DB 접근 최소화 |
| 락 대기 시간          | Redis의 빠른 응답 속도 활용       |
| DB CPU 과부하       | 트래픽 분산으로 DB 부하 감소        |

<br>

### 구현 방식

Redisson의 분산 락을 활용했습니다.

```java

@Service
@RequiredArgsConstructor
public class CouponService {

    private final RedissonClient redissonClient;

    public void issueCoupon(Long couponId, Long userId) {
        RLock lock = redissonClient.getLock("coupon:lock:" + couponId);

        try {
            boolean acquired = lock.tryLock(10, 5, TimeUnit.SECONDS);

            if (acquired) {
                // 락 획득 성공 시에만 DB 접근
                Coupon coupon = couponRepository.findById(couponId).orElseThrow();
                if (coupon.getQuantity() > 0) {
                    coupon.decrease();
                    issuedCouponRepository.save(new IssuedCoupon(couponId, userId));
                }
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

<br>

**Redisson을 선택한 이유:**

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

Pub/Sub 기반의 Redisson이 대용량 트래픽 상황에서 더 효율적입니다.
<br>

### 부하 테스트 결과

**[테스트 결과 - 업데이트 예정]**

| 지표              | Phase 1 | Phase 2 | 개선율 |
|-----------------|:-------:|:-------:|:---:|
| **TPS**         |    -    |    -    |  -  |
| **Avg Latency** |    -    |    -    |  -  |
| **P99 Latency** |    -    |    -    |  -  |
| **DB CPU**      |    -    |    -    |  -  |

<br>

### 남은 과제

Redis 분산 락으로 성능이 향상되었지만, 아직 해결되지 않은 문제가 있었습니다.
<br>

**불필요한 트래픽이 여전히 서버에 도달**

```
[문제 상황]

총 요청: 10,000건
쿠폰 수량: 1,000개

→ 1,001번째 이후 요청도 모두 서버에 도달
→ 락 획득 시도 후 재고 없음 확인
→ 불필요한 리소스 사용
```

쿠폰이 소진된 후에도 모든 요청이 서버까지 도달하여 처리됩니다.

이 트래픽을 사전에 차단할 수 있다면 시스템 부하를 더욱 줄일 수 있습니다.
<br>

### Phase 2 결론

Redis 분산 락으로 DB 병목은 해소되었지만,
**불필요한 트래픽을 사전에 차단하는 메커니즘**이 필요합니다.
<br>

---

## Phase 3: Redis 기반 트래픽 제어

### 목표

**처리 불가능한 요청을 최대한 빨리 거절**하여 시스템 부하를 최소화합니다.

쿠폰이 소진된 후의 요청은 Redis 레벨에서 즉시 응답하여 서버 리소스를 보호합니다.
<br>

### 아키텍처

```
[Client] → [Redis (Traffic Control)] → [Spring Boot Server] → [MySQL]
                    ↓
            (Fast Fail: 즉시 거절)
```

<br>

### Phase 2 대비 개선 포인트

| Phase 2 문제      | Phase 3 해결 방안     |
|-----------------|-------------------|
| 모든 요청이 서버에 도달   | Redis에서 사전 필터링    |
| 재고 소진 후에도 처리 시도 | 즉시 거절 (Fast Fail) |
| 서버 리소스 낭비       | 유효한 요청만 서버로 전달    |

<br>

### 구현 방식

**Redis INCR을 활용한 원자적 카운터**

```java

@Service
@RequiredArgsConstructor
public class CouponService {

    private final StringRedisTemplate redisTemplate;
    private static final int MAX_COUPON_COUNT = 1000;

    public CouponResult tryIssueCoupon(Long couponId, Long userId) {
        String key = "coupon:count:" + couponId;

        // Redis INCR: 원자적 연산으로 동시성 보장
        Long count = redisTemplate.opsForValue().increment(key);

        if (count <= MAX_COUPON_COUNT) {
            // 선착순 1,000명 이내 → 발급 진행
            return CouponResult.SUCCESS;
        } else {
            // 1,001번째 이후 → 즉시 거절 (DB 접근 없음)
            redisTemplate.opsForValue().decrement(key);
            return CouponResult.SOLD_OUT;
        }
    }
}
```

<br>

**핵심: Fast Fail 전략**

```
[트래픽 흐름 비교]

Phase 2:
요청 10,000건 → 서버 10,000건 처리 → DB 1,000건 접근

Phase 3:
요청 10,000건 → Redis 필터링 → 서버 1,000건만 처리 → DB 1,000건 접근
                    ↓
              9,000건 즉시 거절 (Fast Fail)
```

<br>

### 부하 테스트 결과

**[스트레스 테스트 조건]**

- 동시 사용자: 10,000명
- 총 요청: 100,000건
- 쿠폰 수량: 1,000개
  <br>

**[테스트 결과 - 업데이트 예정]**

| 지표              | Phase 2 | Phase 3 | 개선율 |
|-----------------|:-------:|:-------:|:---:|
| **TPS**         |    -    |    -    |  -  |
| **Avg Latency** |    -    |    -    |  -  |
| **성공 발급**       |    -    | 1,000건  |  -  |
| **Fast Fail**   |    -    | 99,000건 |  -  |
| **서버 안정성**      |    -    |   정상    |  -  |

<br>

### 핵심 성과

Phase 3의 가장 큰 성과는 **대용량 트래픽에서도 시스템이 안정적으로 동작**한다는 점입니다.

1. **Fast Fail**: 발급 불가 요청은 Redis에서 즉시 응답 (수십 μs)
2. **서버 보호**: 불필요한 트래픽이 서버에 도달하지 않음
3. **확장성**: Redis 클러스터로 더 높은 TPS 달성 가능
   <br>

### Phase 3 결론

Redis 기반 트래픽 제어로 **시스템 안정성**을 확보했습니다.

핵심 인사이트는 "**모든 요청을 정직하게 처리하는 것보다, 처리 불가능한 요청을 빨리 거절하는 것**"이 대용량 트래픽 처리의 핵심이라는 점입니다.
<br>

---

## Phase 4: 성능 최적화 및 최대 TPS 도출

### 목표

시스템이 처리할 수 있는 **최대 TPS를 도출**하고, 병목 지점을 식별하여 최적화합니다.
<br>

### 최적화 영역

**1. JVM 튜닝**

```bash
# GC 튜닝: G1GC 사용, 최대 pause time 설정
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-Xms2g -Xmx2g
```

<br>

**2. Connection Pool 최적화**

```yaml
# HikariCP 설정
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 3000
```

<br>

**3. Redis 커넥션 풀 최적화**

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 100
          max-idle: 50
```

<br>

### 부하 테스트 방법론

체계적인 성능 측정을 위해 다양한 테스트 방법론을 적용했습니다.

| 테스트 유형        | 목적        | 방법                         |
|---------------|-----------|----------------------------|
| **Ramp-up**   | 한계점 도출    | 부하를 점진적으로 증가시켜 성능 저하 시점 확인 |
| **Stress**    | 시스템 한계 확인 | 예상 최대치 이상의 부하를 가해 동작 확인    |
| **Spike**     | 순간 폭주 대응  | 갑작스러운 트래픽 급증 시 동작 확인       |
| **Endurance** | 장시간 안정성   | 일정 부하를 장시간 유지하여 메모리 누수 확인  |

<br>

### 최종 성능 측정 결과

**[최종 결과 - 업데이트 예정]**

| 지표              | Phase 1 | Phase 2 | Phase 3 | Phase 4 | 총 개선율 |
|-----------------|:-------:|:-------:|:-------:|:-------:|:-----:|
| **Max TPS**     |    -    |    -    |    -    |    -    |   -   |
| **Avg Latency** |    -    |    -    |    -    |    -    |   -   |
| **P99 Latency** |    -    |    -    |    -    |    -    |   -   |
| **Fail Rate**   |    -    |    -    |    -    |    -    |   -   |

<br>

---

## 아키텍처 진화 요약

4단계에 걸친 아키텍처 진화 과정과 각 단계별 핵심 개선 포인트입니다.

```
Phase 1: [Client] → [Server] → [MySQL]
         │
         └─ 병목: DB Connection 고갈, 낮은 TPS

Phase 2: [Client] → [Server] → [Redis Lock] → [MySQL]
         │
         └─ 개선: DB 병목 해소, TPS 향상
         └─ 남은 과제: 불필요한 트래픽 처리

Phase 3: [Client] → [Redis Traffic Control] → [Server] → [MySQL]
         │
         └─ 개선: Fast Fail로 시스템 안정성 확보
         └─ 핵심: 처리 불가 요청 조기 차단

Phase 4: [Client] → [Redis Traffic Control] → [Optimized Server] → [MySQL]
         │
         └─ 개선: 튜닝으로 최대 TPS 도출
```

<br>

### 핵심 의사결정

| 결정 사항            | 근거                            |
|------------------|-------------------------------|
| MySQL → Redis 도입 | 인메모리 기반 고성능, 초당 수십만 건 처리 가능   |
| 분산 락 → 트래픽 제어    | Fast Fail로 불필요한 부하 제거         |
| Redisson 선택      | Pub/Sub 기반 효율적 락, 대용량 트래픽에 적합 |
| nGrinder 선택      | 분산 부하 테스트로 실제 트래픽 시뮬레이션       |

<br>

---

## 결론 및 배운 점

### 단계별 성과 요약

| 단계          | 주요 개선              | 효과               |
|-------------|--------------------|------------------|
| Phase 1 → 2 | Redis 분산 락 도입      | DB 병목 해소, TPS 향상 |
| Phase 2 → 3 | 트래픽 제어 (Fast Fail) | 시스템 안정성 확보       |
| Phase 3 → 4 | JVM/DB/Redis 튜닝    | 최대 TPS 도출        |

<br>

### 대용량 트래픽 처리 핵심 인사이트

**1. 병목 지점을 앞단으로 이동**

DB보다 Redis, Redis보다 앞단에서 처리할수록 TPS가 향상됩니다.
<br>

**2. Fast Fail 전략**

처리 불가능한 요청은 최대한 빨리 거절하는 것이 시스템 전체 처리량을 높입니다.
<br>

**3. 계층별 역할 분리**

트래픽 제어(Redis) → 비즈니스 로직(Server) → 데이터 저장(DB)으로 역할을 분리하면
각 계층을 독립적으로 확장할 수 있습니다.
<br>

**4. 정량적 측정의 중요성**

"느리다/빠르다"가 아닌 TPS, Latency, Fail Rate 등 구체적인 수치로 개선 효과를 증명해야 합니다.
<br>

---

## 향후 계획

### 추가 성능 테스트

- **Spike 테스트**: 갑작스러운 10배 트래픽 급증 시 대응
- **Soak 테스트**: 장시간(24시간+) 부하에서 메모리 누수 확인
- **다양한 인스턴스 스펙별 TPS 비교**: t3.medium vs t3.large vs t3.xlarge
  <br>

### 모니터링 및 관측성(Observability)

- **Prometheus + Grafana**: 실시간 TPS, Latency 대시보드
- **Pinpoint APM**: 병목 구간 시각화, 트랜잭션 추적
- **AWS CloudWatch**: 인프라 레벨 모니터링
  <br>

### 아키텍처 확장

- **Kafka 기반 비동기 처리**: 쿠폰 발급을 이벤트로 처리하여 더 높은 TPS 달성
- **Redis Cluster**: 단일 Redis 한계 극복, 수평 확장
- **멀티 리전**: 글로벌 서비스를 위한 지역별 분산 처리
  <br>

---

## 마치며

이번 글에서는 선착순 쿠폰 발급 시스템을 구축하면서
대용량 트래픽을 처리하기 위해 시도한 다양한 전략을 공유했습니다.

RDBMS 기반의 단순한 구현에서 시작해 Redis를 활용한 고성능 아키텍처로 진화시키면서,
**"어떻게 하면 더 많은 트래픽을 안정적으로 처리할 수 있을까?"** 를 끊임없이 고민했습니다.

그 과정에서 얻은 핵심 인사이트는 **"처리 불가능한 요청을 빨리 거절하는 것"** 이 대용량 트래픽 처리의 핵심이라는 점입니다.

앞으로도 각 Phase별 상세 테스트 결과와 추가적인 최적화 과정을 업데이트할 예정입니다.

대규모 트래픽을 다루는 시스템을 설계하시는 분들께 도움이 되길 바랍니다.
<br>

---

## 참고 자료

- [Redisson GitHub](https://github.com/redisson/redisson)
- [nGrinder GitHub](https://github.com/naver/ngrinder)
  <br>

---

## 변경 이력

|  버전  |     날짜     | 변경 내용 |
|:----:|:----------:|:-----:|
| v1.0 | 2025-12-14 | 초안 작성 |
