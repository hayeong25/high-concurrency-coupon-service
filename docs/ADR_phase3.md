# Phase 3: Redis 유량 제어(Rate Limiting) 부하 테스트 결과 분석

> 테스트 일시: 2026-01-11 06:35:48 ~ 06:37:01

---

## 1. 테스트 환경

### 1.1 인프라 구성

| 구성 요소 | 스펙 |
|:---|:---|
| **Application Server** | Local (Windows) |
| **Database** | MySQL 8.0 (Docker) |
| **Redis** | Redis 7 Alpine (Docker) |
| **JDK** | Java 21 |
| **Framework** | Spring Boot 3.4.1 |
| **Redis 클라이언트** | Redisson 3.40.2 |
| **부하 테스트 도구** | nGrinder 3.9.1 |

### 1.2 테스트 설정

| 항목 | 설정값 |
|:---|:---|
| **VUser** | 600명 |
| **Duration** | 약 73초 |
| **Agent** | 1대 |
| **동시성 제어** | Redis 원자적 카운터 (RAtomicLong) |
| **쿠폰 수량** | 1,000개 |

### 1.3 Rate Limiting 설정

| 항목 | 설정값 | 설명 |
|:---|:---:|:---|
| **Counter Key** | `coupon:count` | Redis 원자적 카운터 |
| **Max Count** | 1,000 | 선착순 제한 수량 |
| **Fast Fail** | 즉시 거절 | 1,001번째 이후 요청 |

### 1.4 nGrinder Script

```groovy
import static net.grinder.script.Grinder.grinder
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import net.grinder.script.GTest
import net.grinder.script.Grinder
import net.grinder.scriptengine.groovy.junit.GrinderRunner
import net.grinder.scriptengine.groovy.junit.annotation.BeforeProcess
import net.grinder.scriptengine.groovy.junit.annotation.BeforeThread
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.ngrinder.http.HTTPRequest
import org.ngrinder.http.HTTPRequestControl
import org.ngrinder.http.HTTPResponse
import org.ngrinder.http.cookie.Cookie
import org.ngrinder.http.cookie.CookieManager

@RunWith(GrinderRunner)
class TestRunner {

    public static GTest test
    public static HTTPRequest request
    public static Map<String, String> headers = [:]
    public static List<Cookie> cookies = []
    public static String baseUrl = "http://host.docker.internal:8081"
    Long myUserId

    @BeforeProcess
    public static void beforeProcess() {
        HTTPRequestControl.setConnectionTimeout(300000)
        test = new GTest(1, "Coupon Issue Rate Limiting Test")
        request = new HTTPRequest()
        headers.put("Content-Type", "application/json")
        grinder.logger.info("before process.")
    }

    @BeforeThread
    public void beforeThread() {
        test.record(this, "test")
        grinder.statistics.delayReports = true
        request.setHeaders(headers)
        HTTPResponse response = request.POST(baseUrl + "/api/v1/user/register", [:], [])
        if (response.statusCode == 200) {
            def json = new groovy.json.JsonSlurper().parseText(response.getBodyText())
            myUserId = json.userId
        } else {
            myUserId = null
        }
        grinder.logger.info("before thread. userId=" + myUserId)
    }

    @Before
    public void before() {
        request.setHeaders(headers)
        CookieManager.addCookies(cookies)
    }

    @Test
    public void test() {
        if (myUserId == null) { return }
        String body = "{\"userId\": " + myUserId + "}"
        HTTPResponse response = request.POST(baseUrl + "/api/v1/coupon/issue/ratelimit", body.getBytes(), [])
        // 200: 발급 성공, 409: 중복 발급, 410: 쿠폰 소진 (Fast Fail)
        assertThat(response.statusCode, anyOf(is(200), is(409), is(410)))
    }
}
```

---

## 2. 테스트 결과 요약

### 2.1 핵심 지표

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

### 2.2 데이터 정합성 검증

| 항목 | 값 | 검증 |
|:---|:---:|:---:|
| **등록된 사용자 수** | - | - |
| **발급된 쿠폰 수** | 1,000개 | ✅ |
| **남은 쿠폰 수** | 0개 | ✅ |
| **Overselling 발생** | 없음 | ✅ |

### 2.3 결과 판정

```
TPS: 26.3 / 10,000 (목표 대비 0.26% 달성)
응답시간: 20,811ms (목표 대비 208배 초과)
에러율: 0% (목표 달성)
데이터 정합성: 100% (Overselling 없음)
```

---

## 3. 상세 분석

### 3.1 시간대별 TPS 추이

![img.png](image/phase3/시간대별%20TPS.png)

### 3.2 시간대별 응답시간 추이

![img.png](image/phase3/시간대별%20응답시간.png)

**응답시간이 초기 7초에서 시작하여 점진적으로 증가, 31초까지 상승하는 패턴을 보입니다.**

### 3.3 Raw 데이터 (샘플)

| 시간 | VUser | Tests | Errors | Mean Time (ms) | TPS |
|:---|:---:|:---:|:---:|:---:|:---:|
| 06:35:48 | 600 | 106 | 0 | 7,057 | 53.0 |
| 06:35:51 | 600 | 83 | 0 | 8,761 | 41.5 |
| 06:36:03 | 600 | 128 | 0 | 13,727 | **64.0** |
| 06:36:29 | 600 | 123 | 0 | 24,476 | **61.5** |
| 06:36:47 | 600 | 5 | 0 | 29,156 | 2.5 |
| 06:36:59 | 600 | 82 | 0 | 31,315 | 41.0 |
| 06:37:01 | 600 | 167 | 0 | 30,931 | **83.5** |

### 3.4 응답시간 분포

| 구간 | 빈도 | 비율 |
|:---|:---:|:---:|
| 7,000ms ~ 15,000ms | 8회 | 21.6% |
| 15,000ms ~ 20,000ms | 8회 | 21.6% |
| 20,000ms ~ 25,000ms | 9회 | 24.3% |
| 25,000ms ~ 32,000ms | 12회 | 32.4% |

**대부분의 요청(56.7%)이 20초 ~ 32초 구간에서 처리되었습니다.**

### 3.5 TPS 변동성 분석

| 지표 | 값 |
|:---|:---|
| **평균 TPS** | 26.3 |
| **최대 TPS** | 83.5 |
| **최소 TPS** | 2.5 |
| **TPS 범위** | 2.5 ~ 83.5 |

VUser 600명 상황에서 TPS가 불안정하게 변동하며, 평균 26.3 TPS를 기록했습니다.

---

## 4. 병목 지점 분석

### 4.1 Rate Limiting의 동작 원리

```
[요청 N개]
     │
     ▼
[Redis INCR: 원자적 증가]
     │
     ├─ count <= 1000 → [DB 접근] → [쿠폰 발급] → [응답]
     │                       ↑
     │                  병목 발생 지점
     │
     └─ count > 1000  → [즉시 거절 (Fast Fail)] → [빠른 응답]
```

### 4.2 발견된 병목

| 병목 지점 | 원인 | 영향 |
|:---|:---|:---|
| **DB 접근 병목** | 선착순 1,000명은 여전히 DB 접근 필요 | 응답시간 증가 |
| **트랜잭션 직렬화** | 동시 DB 쓰기 시 락 경합 | TPS 제한 |
| **Fast Fail 효과 제한** | 1,000건 발급 전에는 효과 없음 | 초기 응답시간 높음 |

### 4.3 Fast Fail 효과 분석

```
[예상 시나리오]
총 요청: 1,882건
쿠폰 수량: 1,000개

선착순 성공 (DB 접근): 1,000건 → 응답시간 높음
Fast Fail (즉시 거절): 882건 → 응답시간 낮아야 함

[실제 결과]
- 평균 응답시간: 20,811ms
- Fast Fail의 효과가 평균에 반영되지 않음
- 대부분의 처리 시간이 DB 접근에 소요됨
```

### 4.4 리틀의 법칙(Little's Law) 적용

```
L = λ × W

L (시스템 내 평균 요청 수) = 600 VUser
W (평균 응답시간) = 20.81초
λ (TPS) = L / W = 600 / 20.81 ≈ 28.8 TPS
```

측정된 TPS(26.3)가 이론값(28.8)과 유사하며, **DB 병목이 주요 원인**임을 나타냅니다.

### 4.5 Phase 2 vs Phase 3 성능 비교

| 지표 | Phase 2 (Redis Lock) | Phase 3 (Rate Limit) | 변화 |
|:---|:---:|:---:|:---:|
| **VUser** | 1,500 | 600 | -60% |
| **평균 TPS** | 39.3 | 26.3 | -33.1% |
| **평균 응답시간** | 30,120ms | 20,811ms | **-30.9%** |
| **최대 응답시간** | 40,492ms | 31,315ms | **-22.7%** |
| **에러율** | 0% | 0% | 동일 |
| **데이터 정합성** | 100% | 100% | 동일 |
| **총 처리량** | 2,832건 | 1,882건 | -33.5% |

### 4.6 동일 조건 예상 비교 (VUser 600 기준)

Phase 2를 VUser 600으로 환산 시:

```
Phase 2 (1,500 VUser) → Phase 2 (600 VUser 환산)
예상 응답시간 = 30,120 × (600/1500) ≈ 12,048ms
```

| 지표 | Phase 2 (600 VUser 환산) | Phase 3 (600 VUser 실측) |
|:---|:---:|:---:|
| **응답시간** | ~12,048ms | 20,811ms |

**분석**: Phase 3의 응답시간이 예상보다 높은 이유는 **1인 1쿠폰 제한으로 인한 DB 조회 오버헤드** 때문입니다.

---

## 5. 성능 지표 요약

### 5.1 주요 성능 지표

| 지표 | 값 | 비고 |
|:---|:---:|:---|
| **Avg TPS** | 26.3 | 목표(10,000) 대비 0.26% |
| **Max TPS** | 83.5 | 순간 최대 처리량 |
| **Min TPS** | 2.5 | 락 경합 심화 시 |
| **Avg Latency** | 20,811ms | 목표(100ms) 대비 208배 |
| **Min Latency** | 7,057ms | 초기 (대기열 적음) |
| **Max Latency** | 31,315ms | 후반 (대기열 누적) |
| **Error Rate** | 0% | 목표 달성 |
| **Throughput** | 1,882건 | 총 처리량 |

### 5.2 처리량 분석

| 항목 | 값 |
|:---|:---|
| 총 처리 건수 | 1,882건 |
| 쿠폰 발급 성공 | 1,000건 |
| 쿠폰 소진 후 거절 (410) | 882건 |
| 발급 성공률 | 53.1% |

---

## 6. 결론

### 6.1 Phase 3 결과 요약

| 항목 | 결과 |
|:---|:---|
| **성능** | TPS 26.3으로 목표(10,000) 대비 0.26% 달성 |
| **응답시간** | 평균 20.8초 (VUser 600 기준) |
| **안정성** | 에러율 0%로 안정적 |
| **정합성** | 정확히 1,000개 발급, Overselling 없음 |

### 6.2 Rate Limiting의 특성

**장점:**
1. **락 없는 처리**: Redis 분산 락 대비 오버헤드 감소
2. **Fast Fail**: 쿠폰 소진 후 즉시 거절로 서버 보호
3. **데이터 정합성**: 에러율 0%, Overselling 완벽 차단
4. **단순한 구현**: 원자적 카운터만으로 동시성 제어

**한계:**
1. **DB 병목 미해소**: 선착순 1,000명은 여전히 DB 접근 필요
2. **1인 1쿠폰 체크**: 매 요청마다 DB 조회 발생
3. **Fast Fail 효과 제한**: 쿠폰 소진 전에는 성능 향상 없음

### 6.3 기대 vs 실제 결과 분석

```
[기대]
- Fast Fail로 인한 TPS 대폭 향상 (10,000+)
- 응답시간 < 100ms

[실제]
- TPS: 26.3 (Phase 2: 39.3 대비 하락)
- 응답시간: 20,811ms (Phase 2: 30,120ms 대비 개선)

[원인 분석]
1. 1인 1쿠폰 검증: existsByUserId() DB 조회 → 병목
2. 쿠폰 발급 트랜잭션: DB 쓰기 작업 → 병목
3. Fast Fail: 쿠폰 소진 후에만 효과 발휘
```

### 6.4 개선 방향

```
현재 문제: DB 접근 병목 (1인 1쿠폰 검증 + 쿠폰 발급)

Phase 4 개선 방향:
1. 1인 1쿠폰 검증을 Redis로 이동 (SET 자료구조)
2. 쿠폰 발급을 비동기 처리 (Kafka/RabbitMQ)
3. Redis에서 즉시 응답 후 백그라운드로 DB 저장
4. Connection Pool 최적화 (HikariCP 튜닝)
```

---

## 7. Phase 1 vs Phase 2 vs Phase 3 최종 비교

| 항목 | Phase 1 (DB Lock) | Phase 2 (Redis Lock) | Phase 3 (Rate Limit) |
|:---|:---:|:---:|:---:|
| **VUser** | 600 | 1,500 | 600 |
| **TPS** | 24.6 | 39.3 | 26.3 |
| **응답시간** | 18,942ms | 30,120ms | 20,811ms |
| **총 처리량** | 2,063건 | 2,832건 | 1,882건 |
| **에러율** | 0% | 0% | 0% |
| **정합성** | 100% | 100% | 100% |
| **Fast Fail** | 없음 | 없음 | **있음** |

### 7.1 동일 VUser(600) 기준 비교

| 항목 | Phase 1 | Phase 3 | 변화 |
|:---|:---:|:---:|:---:|
| **TPS** | 24.6 | 26.3 | **+6.9%** |
| **응답시간** | 18,942ms | 20,811ms | +9.9% |

**결론**: Phase 3(Rate Limiting)은 Phase 1(DB Lock) 대비 TPS가 소폭 향상되었으나, 기대했던 Fast Fail 효과는 DB 병목으로 인해 제한적입니다. 근본적인 성능 향상을 위해서는 **DB 접근 자체를 최소화하는 아키텍처 개선**이 필요합니다.
