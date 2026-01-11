# 🎟️ 선착순 쿠폰 발급 시스템

> 대규모 트래픽(TPS 10,000+) 환경에서 발생하는 동시성 이슈와 시스템 병목을 단계적으로 해결하며, **선착순 쿠폰 발급 시스템**의 아키텍처를 진화시키는 과정을 기록합니다.

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?style=flat-square&logo=springboot)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7.x-DC382D?style=flat-square&logo=redis&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-8.x-02303A?style=flat-square&logo=gradle)

<br>

## 📌 Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Development Roadmap](#development-roadmap)
- [Performance Metrics](#performance-metrics)
- [Deliverables](#deliverables)
- [Future Work](#future-work)

<br>

## Overview

### 프로젝트 주제
10,000원 할인 쿠폰 선착순 발급 시스템 구축
- 오전 10시에 쿠폰 발급 버튼 활성화
- 선착순 **1,000명**에게만 쿠폰 발급
- 수량 소진 시 이벤트 마감

<br>

### 핵심 문제 (Pain Point)
| 문제 | 설명 |
|:---|:---|
| DB Connection Pool 고갈 | 동시 접속자 폭주로 인한 커넥션 부족 |
| Race Condition | 쿠폰 수량 차감 시 발생하는 경쟁 상태 및 데이터 정합성 깨짐 |
| Overselling | 재고 수량 초과 발급 방지 필요 |
| Latency & Downtime | 사용자 요청 지연 및 서버 다운 |

<br>

## Tech Stack

| Category  |     Technology     |
|:---------:|:------------------:|
| Language  |      Java 21       |
| Framework | Spring Boot 3.4.1  |
| Database  |     MySQL 8.0      |
| Cache     |       Redis        |
| Infra     |        AWS         |
| Load Test |      nGrinder      |
| Build     |       Gradle       |

<br>

## Architecture

```
[Phase 1] Client → Server → MySQL (DB Lock)
[Phase 2] Client → Server → Redis (Distributed Lock) → MySQL
[Phase 3] Client → Server → Redis (Atomic Counter) → MySQL
[Phase 4] Client → Server → Redis (Counter + SET) → MySQL  ← 현재
```

<br>

## Development Roadmap

### 📉 Phase 1: 비관적 락 (RDBMS Only)
- **아키텍처**: `Client` → `Server` → `MySQL (Pessimistic Lock)`
- **구현 목표**:
  - 기본적인 쿠폰 발급 기능 구현
  - JPA의 **비관적 락(Pessimistic Lock)** - DB Lock 기반 동시성 제어
- **검증(Test)**: nGrinder로 동시 요청 발생 시 TPS, DB CPU 사용률, 정합성 측정
- **예상 문제**:
  - 락 대기 시간으로 인한 성능 저하, DB Connection 고갈

### ⚡ Phase 2: Redis 분산 락 (Distributed Lock)
- **아키텍처**: `Client` → `Server` → `Redis (Redisson Distributed Lock)` → `MySQL`
- **개선 목표**:
  - DB 락을 제거하고, **Redis 분산 락(Distributed Lock)**을 통해 동시성 제어 부하를 인메모리로 이동
  - Redisson의 RLock을 활용한 분산 환경 동시성 제어
- **검증(Test)**: Phase 1 대비 TPS 향상률 및 응답 시간(Latency) 단축 측정
- **남은 문제**: 락 획득을 위한 스핀락(Spin Lock) 부하, 여전히 DB에 직접 닿는 트래픽 존재

### 🚦 Phase 3: Redis 유량 제어 (Rate Limiting)
- **아키텍처**: `Client` → `Redis (Atomic Counter)` → `Server` → `MySQL`
- **구현 내용**:
  - **Redis INCR 원자적 카운터** 기반 선착순 제어
  - **Fast Fail 전략**: 선착순 1,000명 초과 시 즉시 거절 (DB 접근 없음)
  - 락 없이 원자적 연산만으로 동시성 제어
- **API 엔드포인트**: `POST /api/v1/coupon/issue/ratelimit`
- **핵심 개선**:
  - Phase 2의 단일 글로벌 락 → 락 없는 병렬 처리
  - 직렬화 처리 → 병렬 처리로 TPS 대폭 향상 기대
  - 불필요한 DB 트래픽 차단으로 서버 보호
- **검증(Test)**: 대량 접속자 상황에서 Fast Fail 동작 및 데이터 정합성 확인 예정

### 🚀 Phase 4: 최적화된 Redis 기반 처리 (Optimized)
- **아키텍처**: `Client` → `Server` → `Redis (Counter + SET)` → `MySQL`
- **구현 내용**:
  - **Redis 체크 우선 처리**: 모든 Redis 검증을 DB 접근 전에 수행
  - **Redis SET 중복 체크**: DB 조회 없이 O(1) 중복 검증
  - **두 단계 Fast Fail**: 수량 체크 + 중복 체크 모두 Redis에서 처리
- **API 엔드포인트**: `POST /api/v1/coupon/issue/optimized`
- **핵심 개선**:
  - 쿠폰 소진 후 요청: Redis에서 즉시 거절 (DB 접근 0회)
  - 중복 발급 요청: Redis SET에서 즉시 거절 (DB 접근 0회)
  - 유효한 요청만 DB에 도달
- **검증(Test)**: nGrinder 부하 테스트 완료 - 응답시간 34.5% 개선

<br>

## Performance Metrics

각 Phase 종료 시 아래 표를 채우며 성능 개선을 정량적으로 증명합니다.

| 지표 | Phase 1 (DB Lock) | Phase 2 (Redis Lock) | Phase 3 (Rate Limit) | Phase 4 (Optimized) |
|:---|:---:|:---:|:---:|:---:|
| **VUser** | 600 | 1,500 | 600 | 300 |
| **Avg TPS** | 24.6 | 39.3 | 26.3 | 23.1 |
| **Max TPS** | 47.5 | 60.0 | 83.5 | 63.0 |
| **Avg Latency** | 18,942ms | 30,120ms | 20,811ms | **13,619ms** |
| **Min Latency** | 4,892ms | 7,244ms | 7,057ms | **1,766ms** |
| **Fail Rate** | 0% | 0% | 0% | 0% |
| **Pain Point** | DB 병목 | 락 획득 대기 | DB 접근 병목 | DB 쓰기 병목 |

<br>

## Deliverables

| 산출물 | 설명 |
|:---|:---|
| **GitHub Code** | Phase별 브랜치 관리 (`feat/phase1-db-lock`, `feat/phase2-redis-lock`, `feat/phase3-rate-limit`, `feat/phase4-optimization`) |
| **Performance Report** | nGrinder 결과 그래프 및 분석 리포트 (Wiki 또는 Blog) |
| **ADR** | Architecture Decision Record - 각 Phase별 의사결정 근거 문서 |

<br>

## Future Work

<details>
<summary><b>Phase 1 관련</b></summary>

- **Named Lock (MySQL User-Level Lock)** 비교 테스트
  - `GET_LOCK()` / `RELEASE_LOCK()` 활용
  - 트랜잭션과 독립적으로 동작하는 락 방식

</details>

<details>
<summary><b>Phase 2 관련</b></summary>

- **Lettuce vs Redisson** 성능 비교
  - Lettuce: 스핀락 방식 (CPU 부하 높음)
  - Redisson: Pub/Sub 기반 락 (효율적)
- 락 타임아웃 전략 (waitTime, leaseTime) 최적값 도출
- Redis 고가용성 구성 비교 (Standalone vs Sentinel vs Cluster)

</details>

<details>
<summary><b>Phase 3 관련</b></summary>

- **Redis INCR 원자적 카운터** 방식
  - `INCR coupon:count` → 1,000 초과 시 즉시 거절 (Fast Fail)
  - Redisson RAtomicLong 활용
- **추가 개선 가능 사항**:
  - Sliding Window 기반 Rate Limiter 적용
  - Redis Sorted Set을 활용한 대기열 시스템
  - 유량 제어(서버 보호)와 수량 제어(쿠폰 재고) 분리 설계

</details>

<details>
<summary><b>Phase 4 관련</b></summary>

- **Kafka 기반 비동기 처리**
  - 쿠폰 발급을 이벤트로 처리하여 더 높은 TPS 달성
  - Redis에서 즉시 응답 후 백그라운드로 DB 저장
- **다양한 부하 테스트 방법론 적용**
  - Ramp-up 테스트: 점진적 부하 증가로 한계점 도출
  - Stress 테스트: 한계 초과 시 시스템 동작 확인
  - Endurance 테스트: 장시간 부하에서 메모리 누수 확인
- 모니터링 도구 연동: Prometheus + Grafana, AWS CloudWatch
- APM 도구 적용: Pinpoint 또는 Scouter로 병목 구간 시각화

</details>