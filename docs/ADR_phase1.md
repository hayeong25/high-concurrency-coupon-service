# Phase 1: 비관적 락(Pessimistic Lock) 부하 테스트 결과 분석

> 테스트 일시: 2025-12-21 13:35:40 ~ 13:37:02

---

## 1. 테스트 환경

### 1.1 인프라 구성

| 구성 요소 | 스펙 |
|:---|:---|
| **Application Server** | Local (Windows) |
| **Database** | MySQL 8.0 (Docker) |
| **JDK** | Java 21 |
| **Framework** | Spring Boot 3.4.1 |
| **부하 테스트 도구** | nGrinder 3.9.1 |

### 1.2 테스트 설정

| 항목 | 설정값 |
|:---|:---|
| **VUser** | 600명 (Ramp-up: 300 → 600) |
| **Duration** | 약 82초 |
| **Agent** | 1대 |
| **동시성 제어** | 비관적 락 (Pessimistic Lock) |
| **쿠폰 수량** | 1,000개 |

### 1.3 nGrinder Script
```
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
          test = new GTest(1, "Coupon Issue Test")
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
          HTTPResponse response = request.POST(baseUrl + "/api/v1/coupon/issue/pessimistic", body.getBytes(), [])
          assertThat(response.statusCode, anyOf(is(200), is(409), is(410)))
      }
  }
```

---

## 2. 테스트 결과 요약

### 2.1 핵심 지표

| 지표 | 측정값 | 목표 | 달성 여부 |
|:---|:---:|:---:|:---:|
| **평균 TPS** | 24.6 | 10,000+ | ❌ |
| **최대 TPS** | 134.0 | - | - |
| **최소 TPS** | 0.5 | - | - |
| **평균 응답시간** | 18,942ms | < 100ms | ❌ |
| **최소 응답시간** | 1,652ms | - | - |
| **최대 응답시간** | 28,870ms | - | - |
| **에러율** | 0% | 0% | ✅ |
| **총 처리 건수** | 2,063건 | - | - |

### 2.2 데이터 정합성 검증

| 항목 | 값 | 검증 |
|:---|:---:|:---:|
| **등록된 사용자 수** | 3,000명 | ✅ |
| **발급된 쿠폰 수** | 1,000개 | ✅ |
| **남은 쿠폰 수** | 0개 | ✅ |
| **Overselling 발생** | 없음 | ✅ |

### 2.3 결과 판정

```
TPS: 24.6 / 10,000 (목표 대비 0.25% 달성)
응답시간: 18,942ms (목표 대비 189배 초과)
에러율: 0% (목표 달성)
데이터 정합성: 100% (Overselling 없음)
```

---

## 3. 상세 분석

### 3.1 시간대별 TPS 추이

![img.png](image/phase1/시간대별%20TPS.png)

### 3.2 시간대별 응답시간 추이

![img_1.png](image/phase1/시간대별%20응답시간.png)

**응답시간이 시간이 지남에 따라 급격히 증가하는 패턴을 보입니다.**

### 3.3 락 대기시간 (비관적 락 병목 TTFB)

![img_2.png](image/phase1/락%20대기시간.png)

### 3.4 Raw 데이터 (샘플)

| 시간 | VUser | Tests | Errors | Mean Time (ms) | TPS |
|:---|:---:|:---:|:---:|:---:|:---:|
| 04:35:40 | 300 | 37 | 0 | 1,652 | 18.5 |
| 04:35:44 | 600 | 36 | 0 | 6,129 | 18.0 |
| 04:35:50 | 600 | 38 | 0 | 11,694 | 19.0 |
| 04:36:00 | 600 | 50 | 0 | 16,616 | 25.0 |
| 04:36:10 | 600 | 74 | 0 | 25,318 | 37.0 |
| 04:36:18 | 600 | 109 | 0 | 22,198 | **54.5** |
| 04:36:26 | 600 | 144 | 0 | 15,669 | **72.0** |
| 04:36:48 | 600 | 100 | 0 | 26,893 | 50.0 |
| 04:36:50 | 600 | 268 | 0 | 24,736 | **134.0** |
| 04:37:02 | 600 | 7 | 0 | 28,870 | 3.5 |

### 3.5 응답시간 분포

| 구간 | 빈도 | 비율 |
|:---|:---:|:---:|
| 1,000ms ~ 5,000ms | 2회 | 4.9% |
| 5,000ms ~ 10,000ms | 3회 | 7.3% |
| 10,000ms ~ 15,000ms | 4회 | 9.8% |
| 15,000ms ~ 20,000ms | 9회 | 22.0% |
| 20,000ms ~ 25,000ms | 13회 | 31.7% |
| 25,000ms ~ 30,000ms | 9회 | 22.0% |

**대부분의 요청(75.6%)이 15초 ~ 30초 구간에서 처리되었습니다.**

### 3.6 TPS 변동성 분석

| 지표 | 값 |
|:---|:---|
| **평균 TPS** | 24.6 |
| **최대 TPS** | 134.0 |
| **최소 TPS** | 0.5 |
| **TPS 범위** | 0.5 ~ 134.0 |

VUser 600명 상황에서 TPS가 매우 불안정하게 변동하며, 평균 24.6 TPS로 극히 낮은 처리량을 보입니다.

---

## 4. 병목 지점 분석

### 4.1 비관적 락의 동작 원리

```
[요청 1] ─┐
[요청 2] ─┤
[요청 3] ─┼─▶ [DB Lock 획득 대기] ─▶ [순차 처리] ─▶ [응답]
  ...     │         ↑
[요청 N] ─┘    병목 발생 지점
              (600개 요청이 동시에 락 대기)
```

### 4.2 발견된 병목

| 병목 지점 | 원인 | 영향 |
|:---|:---|:---|
| **DB Lock 대기** | 600 VUser가 동시에 락 획득 시도 | TPS 급락 (24.6) |
| **Connection 점유** | 락 대기 중 커넥션 장시간 점유 | 응답시간 폭증 (최대 28초) |
| **대기열 누적** | 처리 속도 < 요청 속도 | 응답시간 선형 증가 |
| **타임아웃 위험** | 30초 이상 대기 시 연결 종료 가능 | 서비스 장애 위험 |

### 4.3 리틀의 법칙(Little's Law) 적용

```
L = λ × W

L (시스템 내 평균 요청 수) = 600 VUser
W (평균 응답시간) = 18.942초
λ (TPS) = L / W = 600 / 18.942 ≈ 31.7 TPS
```

측정된 TPS(24.6)가 이론값(31.7)보다 낮은 것은 **락 경합으로 인한 추가 오버헤드**가 존재함을 의미합니다.

### 4.4 VUser 증가에 따른 성능 저하 비교

| 지표 | 100 VUser (이전) | 600 VUser (현재) | 변화율 |
|:---|:---:|:---:|:---:|
| **평균 TPS** | 168.3 | 24.6 | **-85.4%** |
| **평균 응답시간** | 613ms | 18,942ms | **+2,990%** |
| **최대 응답시간** | 1,434ms | 28,870ms | **+1,913%** |

VUser가 6배 증가했을 때, TPS는 85% 감소하고 응답시간은 30배 증가했습니다.

### 4.5 처리량 분석

| 항목 | 값 |
|:---|:---|
| 총 처리 건수 | 2,063건 |
| 등록된 사용자 | 3,000명 |
| 쿠폰 발급 성공 | 1,000건 |
| 쿠폰 소진 후 거절 (410) | 1,063건 |
| 발급 성공률 | 48.5% |

---

## 5. 성능 지표 요약

### 5.1 주요 성능 지표

| 지표 | 값 | 비고 |
|:---|:---:|:---|
| **Avg TPS** | 24.6 | 목표(10,000) 대비 0.25% |
| **Max TPS** | 134.0 | 순간 최대 처리량 |
| **Min TPS** | 0.5 | 락 경합 심화 시 |
| **Avg Latency** | 18,942ms | 목표(100ms) 대비 189배 |
| **Min Latency** | 1,652ms | 초기 (락 대기 적음) |
| **Max Latency** | 28,870ms | 락 대기열 최대 시 |
| **Error Rate** | 0% | 목표 달성 |
| **Throughput** | 2,063건 | 총 처리량 |

### 5.2 처리량 vs 응답시간 관계

![img_1.png](image/phase1/시간대별%20응답시간.png)

**시간이 지날수록 대기열이 누적되어 응답시간이 선형적으로 증가합니다.**

---

## 6. 결론

### 6.1 Phase 1 결과 요약

| 항목 | 결과 |
|:---|:---|
| **성능** | TPS 24.6으로 목표(10,000) 대비 0.25% 달성 |
| **응답시간** | 평균 18.9초로 사용자 경험 불가 수준 |
| **안정성** | 에러율 0%로 안정적 |
| **정합성** | 정확히 1,000개 발급, Overselling 없음 |

### 6.2 비관적 락의 한계

1. **처리량 제한**: 락 획득이 순차적으로 이루어져 TPS 상한 존재
2. **응답시간 폭증**: VUser 증가 시 대기시간 지수적 증가
3. **확장성 부족**: 서버 증설로도 DB 락 병목 해소 불가
4. **타임아웃 위험**: 30초 이상 대기로 연결 종료 위험

### 6.3 비관적 락의 장점

1. **구현 단순**: JPA `@Lock` 어노테이션만으로 구현 가능
2. **데이터 정합성**: 에러율 0%, Overselling 완벽 차단
3. **예측 가능성**: 동작 방식이 명확하고 디버깅 용이

### 6.4 개선 방향

```
Phase 2: Redis 분산 락

DB 락 → Redis 락으로 이동

- 인메모리 기반 고속 처리
- DB 부하 감소
- 예상 TPS: 500 ~ 1,000+
- 예상 응답시간: < 1,000ms
```