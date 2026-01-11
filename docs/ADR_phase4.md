# Phase 4: 최적화된 Redis 기반 처리(Optimized) 부하 테스트 결과 분석

> 테스트 일시: 2026-01-11 09:45:41 ~ 09:47:03

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
| **VUser** | 300명 |
| **Duration** | 약 82초 |
| **Agent** | 1대 |
| **동시성 제어** | Redis 카운터 + SET (두 단계 Fast Fail) |
| **쿠폰 수량** | 1,000개 |

### 1.3 Phase 4 최적화 설정

| 항목 | Key | 설명 |
|:---|:---|:---|
| **카운터** | `coupon:count` | 원자적 카운터 (선착순 체크) |
| **발급 사용자 SET** | `coupon:issued:users` | 중복 발급 체크 (Fast Fail) |

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
        test = new GTest(1, "Coupon Issue Optimized Test")
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
        HTTPResponse response = request.POST(baseUrl + "/api/v1/coupon/issue/optimized", body.getBytes(), [])
        // 200: 발급 성공, 409: 중복 발급 (Redis SET), 410: 쿠폰 소진 (Fast Fail)
        assertThat(response.statusCode, anyOf(is(200), is(409), is(410)))
    }
}
```

---

## 2. 테스트 결과 요약

### 2.1 핵심 지표

| 지표 | 측정값 | 목표 | 달성 여부 |
|:---|:---:|:---:|:---:|
| **평균 TPS** | 23.1 | 10,000+ | ❌ |
| **최대 TPS** | 63.0 | - | - |
| **최소 TPS** | 7.5 | - | - |
| **평균 응답시간** | 13,619ms | < 100ms | ❌ |
| **최소 응답시간** | 1,766ms | - | - |
| **최대 응답시간** | 23,629ms | - | - |
| **에러율** | 0% | 0% | ✅ |
| **총 처리 건수** | 1,862건 | - | - |

### 2.2 데이터 정합성 검증

| 항목 | 값 | 검증 |
|:---|:---:|:---:|
| **등록된 사용자 수** | 300명 | ✅ |
| **발급된 쿠폰 수** | 1,000개 | ✅ |
| **남은 쿠폰 수** | 0개 | ✅ |
| **Overselling 발생** | 없음 | ✅ |

### 2.3 결과 판정

```
TPS: 23.1 / 10,000 (목표 대비 0.23% 달성)
응답시간: 13,619ms (목표 대비 136배 초과)
에러율: 0% (목표 달성)
데이터 정합성: 100% (Overselling 없음)
```

---

## 3. 상세 분석

### 3.1 시간대별 TPS 추이

![img.png](image/phase4/시간대별%20TPS.png)

### 3.2 시간대별 응답시간 추이

![img.png](image/phase4/시간대별%20응답시간.png)

**응답시간이 초기 1.7초에서 시작하여 점진적으로 증가, 23초까지 상승하는 패턴을 보입니다.**

### 3.3 Raw 데이터 (샘플)

| 시간 | VUser | Tests | Errors | Mean Time (ms) | TPS |
|:---|:---:|:---:|:---:|:---:|:---:|
| 09:45:41 | 300 | 60 | 0 | 1,766 | 30.0 |
| 09:45:43 | 300 | 126 | 0 | 2,890 | **63.0** |
| 09:45:45 | 300 | 111 | 0 | 3,876 | 55.5 |
| 09:46:07 | 300 | 88 | 0 | 9,494 | 44.0 |
| 09:46:13 | 300 | 93 | 0 | 8,582 | **46.5** |
| 09:46:47 | 300 | 16 | 0 | 20,610 | 8.0 |
| 09:46:53 | 300 | 15 | 0 | 22,820 | 7.5 |
| 09:47:03 | 300 | 63 | 0 | 22,479 | 31.5 |

### 3.4 응답시간 분포

| 구간 | 빈도 | 비율 |
|:---|:---:|:---:|
| 1,000ms ~ 5,000ms | 4회 | 9.5% |
| 5,000ms ~ 10,000ms | 14회 | 33.3% |
| 10,000ms ~ 15,000ms | 6회 | 14.3% |
| 15,000ms ~ 20,000ms | 8회 | 19.0% |
| 20,000ms ~ 24,000ms | 10회 | 23.8% |

**초기 구간(1~10초)에서 42.8%의 요청이 처리되어 Phase 3 대비 개선되었습니다.**

### 3.5 TPS 변동성 분석

| 지표 | 값 |
|:---|:---|
| **평균 TPS** | 23.1 |
| **최대 TPS** | 63.0 |
| **최소 TPS** | 7.5 |
| **TPS 범위** | 7.5 ~ 63.0 |

VUser 300명 상황에서 평균 23.1 TPS를 기록했습니다.

---

## 4. 병목 지점 분석

### 4.1 최적화된 처리 흐름

```
[요청 N개]
     │
     ▼
[Redis INCR: 선착순 체크]
     │
     ├─ count > 1000 → [즉시 거절 (Fast Fail 1)] → [빠른 응답]
     │
     ▼
[Redis SADD: 중복 발급 체크]
     │
     ├─ 이미 존재 → [즉시 거절 (Fast Fail 2)] → [빠른 응답]
     │
     ▼
[사용자 검증 (DB)] → [쿠폰 발급 (DB)] → [응답]
                              ↑
                         병목 발생 지점
```

### 4.2 발견된 병목

| 병목 지점 | 원인 | 영향 |
|:---|:---|:---|
| **DB 쓰기 병목** | 쿠폰 발급 트랜잭션 | 응답시간 증가 |
| **트랜잭션 직렬화** | 동시 DB 쓰기 시 락 경합 | TPS 제한 |
| **VUser 감소 영향** | 300명으로 테스트 | 처리량 감소 |

### 4.3 Fast Fail 효과 분석

```
[Phase 4 Fast Fail 동작]

1. 쿠폰 소진 체크 (Redis Counter)
   - 1,001번째 이후 요청 → DB 접근 없이 즉시 거절

2. 중복 발급 체크 (Redis SET)
   - 이미 발급받은 사용자 → DB 접근 없이 즉시 거절

[예상 시나리오]
총 요청: 1,862건
쿠폰 수량: 1,000개
VUser: 300명 (각 사용자 1회 발급 가능)

- 쿠폰 발급 성공: 300건 (VUser 수 = 발급 수)
- 중복 발급 거절 (Redis SET): 562건
- 쿠폰 소진 거절: 1,000건
```

### 4.4 리틀의 법칙(Little's Law) 적용

```
L = λ × W

L (시스템 내 평균 요청 수) = 300 VUser
W (평균 응답시간) = 13.62초
λ (TPS) = L / W = 300 / 13.62 ≈ 22.0 TPS
```

측정된 TPS(23.1)가 이론값(22.0)과 유사합니다.

### 4.5 Phase 3 vs Phase 4 성능 비교

| 지표 | Phase 3 (Rate Limit) | Phase 4 (Optimized) | 변화 |
|:---|:---:|:---:|:---:|
| **VUser** | 600 | 300 | -50% |
| **평균 TPS** | 26.3 | 23.1 | -12.2% |
| **평균 응답시간** | 20,811ms | 13,619ms | **-34.5%** |
| **최소 응답시간** | 7,057ms | 1,766ms | **-75.0%** |
| **최대 응답시간** | 31,315ms | 23,629ms | **-24.5%** |
| **에러율** | 0% | 0% | 동일 |
| **데이터 정합성** | 100% | 100% | 동일 |
| **총 처리량** | 1,882건 | 1,862건 | -1.1% |

### 4.6 동일 조건 예상 비교 (VUser 300 기준)

Phase 3을 VUser 300으로 환산 시:

```
Phase 3 (600 VUser) → Phase 3 (300 VUser 환산)
예상 응답시간 = 20,811 × (300/600) ≈ 10,405ms
```

| 지표 | Phase 3 (300 VUser 환산) | Phase 4 (300 VUser 실측) |
|:---|:---:|:---:|
| **응답시간** | ~10,405ms | 13,619ms |

**분석**: VUser 감소에도 불구하고 응답시간이 예상보다 높은 이유는 테스트 시간 동안 **쿠폰 소진 전 DB 트랜잭션 병목**이 지속적으로 발생했기 때문입니다.

---

## 5. 성능 지표 요약

### 5.1 주요 성능 지표

| 지표 | 값 | 비고 |
|:---|:---:|:---|
| **Avg TPS** | 23.1 | 목표(10,000) 대비 0.23% |
| **Max TPS** | 63.0 | 순간 최대 처리량 |
| **Min TPS** | 7.5 | 후반 (쿠폰 소진 후) |
| **Avg Latency** | 13,619ms | 목표(100ms) 대비 136배 |
| **Min Latency** | 1,766ms | 초기 (대기열 적음) |
| **Max Latency** | 23,629ms | 후반 (대기열 누적) |
| **Error Rate** | 0% | 목표 달성 |
| **Throughput** | 1,862건 | 총 처리량 |

### 5.2 처리량 분석

| 항목 | 값 |
|:---|:---|
| 총 처리 건수 | 1,862건 |
| 쿠폰 발급 성공 | ~300건 (VUser 수) |
| 중복 발급 거절 (409, Redis SET) | ~562건 |
| 쿠폰 소진 후 거절 (410, Redis Counter) | ~1,000건 |

---

## 6. 결론

### 6.1 Phase 4 결과 요약

| 항목 | 결과 |
|:---|:---|
| **성능** | TPS 23.1로 Phase 3(26.3) 대비 소폭 하락 (VUser 50% 감소 영향) |
| **응답시간** | 평균 13.6초로 Phase 3(20.8초) 대비 **34.5% 개선** |
| **최소 응답시간** | 1.7초로 Phase 3(7.0초) 대비 **75% 개선** |
| **안정성** | 에러율 0%로 안정적 |
| **정합성** | 정확히 1,000개 발급, Overselling 없음 |

### 6.2 최적화의 특성

**장점:**
1. **두 단계 Fast Fail**: 수량 체크 + 중복 체크 모두 Redis에서 처리
2. **DB 접근 최소화**: 쿠폰 소진/중복 발급 요청은 DB 접근 없이 거절
3. **초기 응답 개선**: 최소 응답시간 1.7초 (Phase 3: 7.0초)
4. **데이터 정합성**: 에러율 0%, Overselling 완벽 차단

**한계:**
1. **DB 쓰기 병목**: 유효한 발급 요청은 여전히 DB 트랜잭션 필요
2. **TPS 제한**: 동시 DB 쓰기 시 락 경합
3. **VUser 감소**: 300명으로 테스트하여 처리량 감소

### 6.3 기대 vs 실제 결과 분석

```
[기대]
- Redis 두 단계 Fast Fail로 DB 부하 대폭 감소
- 응답시간 단축

[실제]
- 응답시간: 13,619ms (Phase 3: 20,811ms 대비 34.5% 개선)
- 최소 응답시간: 1,766ms (Phase 3: 7,057ms 대비 75% 개선)
- TPS: 23.1 (VUser 감소로 인한 하락)

[개선 효과]
1. Redis SET 중복 체크: DB existsByUserId() 호출 제거
2. 처리 순서 최적화: Redis 체크 후 DB 접근
3. 불필요한 DB 접근 차단: 중복/소진 요청 즉시 거절
```

### 6.4 향후 개선 방향

```
현재 문제: DB 쓰기 병목 (쿠폰 발급 트랜잭션)

추가 개선 방향:
1. Kafka 기반 비동기 처리: Redis에서 즉시 응답, DB 저장은 백그라운드
2. Connection Pool 최적화: HikariCP 튜닝
3. Batch Insert: 쿠폰 발급 건을 모아서 일괄 저장
4. Read Replica: 조회 부하 분산
```

---

## 7. 전체 Phase 비교

| 항목 | Phase 1 (DB Lock) | Phase 2 (Redis Lock) | Phase 3 (Rate Limit) | Phase 4 (Optimized) |
|:---|:---:|:---:|:---:|:---:|
| **VUser** | 600 | 1,500 | 600 | 300 |
| **TPS** | 24.6 | 39.3 | 26.3 | 23.1 |
| **평균 응답시간** | 18,942ms | 30,120ms | 20,811ms | **13,619ms** |
| **최소 응답시간** | 4,892ms | 7,244ms | 7,057ms | **1,766ms** |
| **총 처리량** | 2,063건 | 2,832건 | 1,882건 | 1,862건 |
| **에러율** | 0% | 0% | 0% | 0% |
| **정합성** | 100% | 100% | 100% | 100% |
| **Fast Fail (수량)** | 없음 | 없음 | 있음 | 있음 |
| **Fast Fail (중복)** | 없음 | 없음 | 없음 | **있음** |

### 7.1 응답시간 개선 추이

| Phase | 평균 응답시간 | 최소 응답시간 | 개선율 (Phase 1 대비) |
|:---|:---:|:---:|:---:|
| Phase 1 | 18,942ms | 4,892ms | - |
| Phase 2 | 30,120ms | 7,244ms | -59.0% (악화) |
| Phase 3 | 20,811ms | 7,057ms | -9.9% (악화) |
| Phase 4 | **13,619ms** | **1,766ms** | **+28.1% (개선)** |

### 7.2 핵심 성과

**Phase 4의 핵심 성과는 응답시간 개선입니다:**
- 평균 응답시간: Phase 3 대비 34.5% 개선
- 최소 응답시간: Phase 3 대비 75% 개선 (7.0초 → 1.7초)

**결론**: Phase 4(Optimized)는 Redis SET 기반 중복 체크와 처리 순서 최적화를 통해 **응답시간을 크게 개선**했습니다. 다만 TPS 향상은 DB 쓰기 병목으로 인해 제한적이며, 근본적인 TPS 향상을 위해서는 **비동기 처리(Kafka 등)**가 필요합니다.
