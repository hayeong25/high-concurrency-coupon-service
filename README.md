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
[Phase 3] Client → Redis (Rate Limiter) → Server → MySQL
[Phase 4] Performance Optimization & Tuning
```

<br>

## Development Roadmap

### 📉 Phase 1: 낙관적 락 & 비관적 락 (RDBMS Only)
- **아키텍처**: `Client` → `Server` → `MySQL (Optimistic/Pessimistic Lock)`
- **구현 목표**:
  - 기본적인 쿠폰 발급 기능 구현
  - JPA의 **낙관적 락(Optimistic Lock)** - Version 기반 동시성 제어
  - JPA의 **비관적 락(Pessimistic Lock)** - DB Lock 기반 동시성 제어
  - 두 방식의 성능 비교 분석
- **검증(Test)**: nGrinder로 동시 요청 발생 시 TPS, DB CPU 사용률, 정합성 측정
- **예상 문제**:
  - 낙관적 락: 충돌 시 재시도 부하 증가
  - 비관적 락: 락 대기 시간으로 인한 성능 저하, DB Connection 고갈

### ⚡ Phase 2: Redis 분산 락 (Distributed Lock)
- **아키텍처**: `Client` → `Server` → `Redis (Redisson Distributed Lock)` → `MySQL`
- **개선 목표**:
  - DB 락을 제거하고, **Redis 분산 락(Distributed Lock)**을 통해 동시성 제어 부하를 인메모리로 이동
  - Redisson의 RLock을 활용한 분산 환경 동시성 제어
- **검증(Test)**: Phase 1 대비 TPS 향상률 및 응답 시간(Latency) 단축 측정
- **남은 문제**: 락 획득을 위한 스핀락(Spin Lock) 부하, 여전히 DB에 직접 닿는 트래픽 존재

### 🚦 Phase 3: Redis 유량 제어 (Rate Limiting & Traffic Shaping)
- **아키텍처**: `Client` → `Redis (Rate Limiter / Sorted Set)` → `Token 발급` → `Server`
- **개선 목표**:
  - Redis를 활용한 **유량 제어(Rate Limiting)** 구현
  - 서버가 처리 가능한 만큼만 유입시키는 트래픽 쉐이핑
  - Token Bucket / Sliding Window 알고리즘 적용
  - Redis Sorted Set을 활용한 대기열 시스템 구현
- **검증(Test)**: 대량 접속자 상황에서도 서버가 다운되지 않고 일정하게 처리되는지 확인
- **핵심 지표**: **"시스템 안정성(Availability)"** 확보

### 🚀 Phase 4: 최대 TPS 도출 (Performance Optimization)
- **목표**: 시스템의 최대 처리량(Max TPS) 도출을 위한 효율적인 방법론 정립
- **수행 내용**:
  - nGrinder를 활용한 체계적인 부하 테스트 시나리오 설계
  - 병목 지점 분석 (CPU, Memory, Network, DB Connection Pool)
  - JVM 튜닝 및 Spring Boot 최적화
  - AWS 인프라 스케일링 전략 수립
  - 최적의 TPS 도출 및 한계점 분석
- **검증(Test)**: 다양한 부하 조건에서의 성능 측정 및 리포팅

<br>

## Performance Metrics

각 Phase 종료 시 아래 표를 채우며 성능 개선을 정량적으로 증명합니다.

| 지표 | Phase 1 (DB Lock) | Phase 2 (Redis Lock) | Phase 3 (Rate Limit) | Phase 4 (Optimized) |
|:---|:---:|:---:|:---:|:---:|
| **Max TPS** | - | - | - | - |
| **Avg Latency** | - | - | - | - |
| **Fail Rate** | - | - | - | - |
| **Pain Point** | DB 병목 | 락 획득 대기 | 대기 시간 발생 | - |

<br>

## Deliverables

| 산출물 | 설명 |
|:---|:---|
| **GitHub Code** | Phase별 브랜치 관리 (`feat/phase1-db-lock`, `feat/phase2-redis-lock`, `feat/phase3-rate-limit`, `feat/phase4-optimization`) |
| **Performance Report** | nGrinder 결과 그래프 및 분석 리포트 (Wiki 또는 Blog) |
| **ADR** | Architecture Decision Record - 각 Phase별 의사결정 근거 문서 |

### 📝 Next Step
- [ ] Phase 1 환경 구축 (Docker Compose: MySQL, Redis, nGrinder)
- [ ] 기본 쿠폰 발급 로직(Service Layer) 구현
- [ ] AWS 인프라 설계 및 배포 환경 구성

<br>

## Future Work

<details>
<summary><b>Phase 1 관련</b></summary>

- **Named Lock (MySQL User-Level Lock)** 비교 테스트
  - `GET_LOCK()` / `RELEASE_LOCK()` 활용
  - 트랜잭션과 독립적으로 동작하는 락 방식
- 낙관적 락 실패 시 **재시도 전략** 테스트 (재시도 횟수, Exponential Backoff)

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

- **Redis INCR 원자적 카운터** 방식 테스트
  - `INCR coupon:count` → 1,000 초과 시 즉시 거절 (Fast Fail)
  - 락 없이 원자적 수량 제어 가능
- 유량 제어(서버 보호)와 수량 제어(쿠폰 재고) 분리 설계

</details>

<details>
<summary><b>Phase 4 관련</b></summary>

- 다양한 부하 테스트 방법론 적용
  - Ramp-up 테스트: 점진적 부하 증가로 한계점 도출
  - Stress 테스트: 한계 초과 시 시스템 동작 확인
  - Endurance 테스트: 장시간 부하에서 메모리 누수 확인
- 모니터링 도구 연동: Prometheus + Grafana, AWS CloudWatch
- APM 도구 적용: Pinpoint 또는 Scouter로 병목 구간 시각화

</details>