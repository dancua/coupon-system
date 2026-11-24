# coupon-system

선착순 쿠폰 발급 시스템. 요청이 동시에 몰려도 **총 수량보다 많이 발급되지 않고(초과 발급 방지)**, **한 사람이 두 번 받지 못하게(중복 발급 방지)** 하는 것이 목표다.

- Java 17, Spring Boot 4.2, Gradle (Kotlin DSL)
- MySQL 8.4 (Flyway로 스키마 관리), Redis 7.4
- 테스트: JUnit 5, Testcontainers (실제 MySQL / Redis에서 검증)

## 실행

```bash
docker compose up -d --wait   # MySQL, Redis
./gradlew bootRun             # http://localhost:8080 (local 프로필)
```

## 테스트

```bash
./gradlew test             # 전체 테스트 (Docker 필요: Testcontainers가 MySQL / Redis를 띄운다)
./gradlew concurrencyTest  # 동시성 테스트만, 전략별 비교 결과를 콘솔에 출력
```

## API

사용자는 인증 대신 `X-User-Id` 헤더로 식별한다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/coupons` | 쿠폰 정책 생성 |
| GET | `/api/coupons/{couponId}` | 쿠폰 정보 조회 |
| POST | `/api/coupons/{couponId}/issue` | 쿠폰 발급 (선착순, 1인 1매) |
| GET | `/api/issued-coupons` | 내 쿠폰 목록 |
| POST | `/api/issued-coupons/{id}/use` | 쿠폰 사용 |
| POST | `/api/issued-coupons/{id}/cancel` | 사용 취소 |

에러 응답은 `{ code, message, fieldErrors }` 형식이고, `code`는 `CouponErrorCode` 이름(`대상_상황`)이다.

## 핵심 문제: 동시 발급

동시성 제어가 없으면 여러 요청이 같은 발급 수량을 읽고 각자 +1 해서 덮어쓴다(갱신 유실). 그 결과 100장짜리 쿠폰이 수백 장 발급된다.

발급 전략을 `CouponIssuer` 인터페이스로 분리하고, 같은 테스트로 비교했다.

| 전략 | 방식 | 장점 | 단점 |
|---|---|---|---|
| `NoLockCouponIssuer` (테스트 전용) | 읽고 +1 해서 덮어쓰기 | - | 초과 발급 (Before 재현용) |
| `PessimisticLockCouponIssuer` | `SELECT ... FOR UPDATE` | 단순하고 정확 | 모든 요청이 한 행 앞에 줄을 섬 |
| `OptimisticLockCouponIssuer` | `@Version` 충돌 감지 + 재시도 | 충돌이 드물면 빠름 | 선착순처럼 몰리면 재시도 폭증 |
| `RedisCouponIssuer` (**API 기본값**) | Redis Lua 스크립트로 원자적 선점 | DB 락 없이 빠르고, DB에는 수량만큼만 쓰기가 도달 | Redis와 DB 사이 정합성 관리가 필요 |

### 측정 결과 (사용자 1000명이 수량 100장짜리 쿠폰을 동시에 요청, 스레드 32개)

로컬 Mac + Testcontainers, 1회 실행 값이다. 걸린 시간은 환경에 따라 크게 달라지므로 경향만 참고한다.

| 전략 | 발급 수 | 걸린 시간 |
|---|---|---|
| 락 없음 (Before) | **995** (카운터는 100 → 갱신 유실) | 1,688 ms |
| 비관적 락 | 100 | 1,044 ms |
| 낙관적 락 | 100 | 1,739 ms |
| Redis | 100 | 945 ms |

### Redis 발급 흐름

1. **선점 (Lua 스크립트, 원자적)**: 발급 사용자 Set(`coupon:{id}:issued-users`)에 대해 "이미 받았나?(`SISMEMBER`) → 남았나?(`SCARD`) → 추가(`SADD`)"를 한 번에 처리한다.
2. **저장 (DB 트랜잭션)**: 선점에 성공한 요청만 `issued_coupon`을 저장하고, `issued_quantity`를 조건부 UPDATE(`issued_quantity < total_quantity`)로 1 올린다.
3. **보상**: DB 저장이 실패하면 Set에서 사용자를 빼 자리를 돌려준다.

여러 겹으로 방어한다.

- 1차: Redis Lua (수량, 중복)
- 최종: DB 조건부 UPDATE (수량), `(coupon_id, user_id)` unique 제약 (중복)

Redis 데이터가 유실돼도 DB가 초과 발급과 중복 발급을 막는다. 반대로 선점 직후 서버가 죽어 Redis에만 남은 "유령 사용자"는 `RedisCouponIssuer.reconcile(couponId)`가 DB 기준으로 정리한다.

## 남은 과제

- `reconcile`을 주기적으로 실행하는 스케줄러 (현재는 메서드만 있다)
- Redis 장애 시 정책 (발급 중단 또는 DB 락 전략으로 대체)
- Kafka로 발급 요청과 DB 저장 분리 (비동기 발급)
- 만료 쿠폰 정리 스케줄러 (`IssuedCoupon.expire`)
- 부하 테스트 (k6)
