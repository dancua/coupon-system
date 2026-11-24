package io.github.dancua.coupon.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.dancua.coupon.domain.CouponErrorCode;
import io.github.dancua.coupon.domain.DiscountType;
import io.github.dancua.coupon.domain.IssuedCoupon;

/**
 * 세 발급 전략이 모두 같은 발급 규칙을 지키는지 검증한다 (단일 요청 기준).
 */
class CouponIssuerTest extends CouponIssuerTestSupport {

	private static final String NAME = "[{index}] {0}";

	@ParameterizedTest(name = NAME)
	@MethodSource("issuers")
	@DisplayName("발급하면 발급 쿠폰이 저장되고 쿠폰의 발급 수량이 1 늘어난다")
	void issue(Class<? extends CouponIssuer> type) {
		Long couponId = createCoupon(10);

		Long issuedCouponId = issuer(type).issue(couponId, 7L);

		IssuedCoupon issued = issuedCouponRepository.findById(issuedCouponId).orElseThrow();
		assertThat(issued.getUserId()).isEqualTo(7L);
		assertThat(issued.getIssuedAt()).isEqualTo(NOW);
		assertThat(issued.getExpiresAt()).isEqualTo(NOW.plusDays(30));
		assertThat(issuedQuantity(couponId)).isEqualTo(1);
	}

	@ParameterizedTest(name = NAME)
	@MethodSource("issuers")
	@DisplayName("없는 쿠폰은 발급할 수 없다")
	void notFound(Class<? extends CouponIssuer> type) {
		assertCouponError(() -> issuer(type).issue(999_999L, 7L), CouponErrorCode.COUPON_NOT_FOUND);
	}

	@ParameterizedTest(name = NAME)
	@MethodSource("issuers")
	@DisplayName("같은 사용자는 같은 쿠폰을 두 번 받을 수 없고, 발급 수량도 늘지 않는다")
	void alreadyIssued(Class<? extends CouponIssuer> type) {
		Long couponId = createCoupon(10);
		issuer(type).issue(couponId, 7L);

		assertCouponError(() -> issuer(type).issue(couponId, 7L), CouponErrorCode.COUPON_ALREADY_ISSUED);
		assertThat(issuedQuantity(couponId)).isEqualTo(1);
	}

	@ParameterizedTest(name = NAME)
	@MethodSource("issuers")
	@DisplayName("수량이 모두 소진되면 발급할 수 없다")
	void exhausted(Class<? extends CouponIssuer> type) {
		Long couponId = createCoupon(1);
		issuer(type).issue(couponId, 7L);

		assertCouponError(() -> issuer(type).issue(couponId, 8L), CouponErrorCode.COUPON_QUANTITY_EXHAUSTED);
		assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1);
	}

	@ParameterizedTest(name = NAME)
	@MethodSource("issuers")
	@DisplayName("발급 기간이 지난 쿠폰은 발급할 수 없다")
	void notInPeriod(Class<? extends CouponIssuer> type) {
		Long couponId = couponService.createCoupon(new CreateCouponCommand(
			"지난 쿠폰", DiscountType.FIXED, 1000, 10,
			NOW.minusDays(2), NOW.minusDays(1), NOW.plusDays(30)));

		assertCouponError(() -> issuer(type).issue(couponId, 7L), CouponErrorCode.COUPON_NOT_IN_ISSUE_PERIOD);
	}

	@Test
	@DisplayName("[Redis] DB 저장이 실패하면 Redis 선점을 되돌린다 (보상)")
	void redisCompensatesWhenDbFails() {
		Long couponId = createCoupon(10);
		RedisCouponIssuer redisIssuer = context.getBean(RedisCouponIssuer.class);
		redisIssuer.issue(couponId, 7L);
		// Redis 데이터가 유실된 상황을 만든다: DB에는 발급 기록이 있지만 Redis는 모른다
		redisTemplate.delete(RedisCouponIssuer.issuedUsersKey(couponId));

		// Redis 선점은 통과하지만 DB unique 제약이 막는다(최종 방어선)
		assertCouponError(() -> redisIssuer.issue(couponId, 7L), CouponErrorCode.COUPON_ALREADY_ISSUED);

		assertThat(issuedQuantity(couponId)).as("DB 카운터 증가는 롤백된다").isEqualTo(1);
		assertThat(redisTemplate.opsForSet().isMember(RedisCouponIssuer.issuedUsersKey(couponId), "7"))
			.as("실패한 선점은 Redis에서 제거된다").isFalse();
	}

	@Test
	@DisplayName("[Redis] Redis 카운트가 유실돼도 DB 조건부 UPDATE가 초과 발급을 막는다")
	void dbGuardsWhenRedisLost() {
		Long couponId = createCoupon(1);
		RedisCouponIssuer redisIssuer = context.getBean(RedisCouponIssuer.class);
		redisIssuer.issue(couponId, 7L);
		redisTemplate.delete(RedisCouponIssuer.issuedUsersKey(couponId));

		assertCouponError(() -> redisIssuer.issue(couponId, 8L), CouponErrorCode.COUPON_QUANTITY_EXHAUSTED);

		assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1);
		assertThat(redisTemplate.opsForSet().size(RedisCouponIssuer.issuedUsersKey(couponId))).isZero();
	}

	@Test
	@DisplayName("[Redis] 조건부 UPDATE도 @Version을 올려, 낙관적 락 경로가 오래된 값으로 덮어쓰지 못한다")
	void conditionalUpdateBumpsVersion() {
		Long couponId = createCoupon(10);
		long before = couponRepository.findById(couponId).orElseThrow().getVersion();

		context.getBean(RedisCouponIssuer.class).issue(couponId, 7L);

		assertThat(couponRepository.findById(couponId).orElseThrow().getVersion()).isEqualTo(before + 1);
	}

	@Test
	@DisplayName("[Redis] Redis와 낙관적 락 경로를 번갈아 써도 카운터와 발급 행 수가 일치한다")
	void mixedStrategiesKeepCounter() {
		Long couponId = createCoupon(10);
		context.getBean(RedisCouponIssuer.class).issue(couponId, 7L);
		context.getBean(OptimisticLockCouponIssuer.class).issue(couponId, 8L);
		context.getBean(RedisCouponIssuer.class).issue(couponId, 9L);

		assertThat(issuedQuantity(couponId)).isEqualTo(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(3);
	}

	@Test
	@DisplayName("[Redis] DB에 없는 유령 사용자는 맞추기(reconcile)로 지워지고, 그 사용자는 다시 발급받을 수 있다")
	void reconcileRemovesGhosts() {
		Long couponId = createCoupon(2);
		RedisCouponIssuer redisIssuer = context.getBean(RedisCouponIssuer.class);
		String key = RedisCouponIssuer.issuedUsersKey(couponId);
		redisIssuer.issue(couponId, 7L);
		// 선점 직후 서버가 죽어 DB 저장도, 보상도 못 한 상황: Redis에만 99번 사용자가 남는다
		redisTemplate.opsForSet().add(key, "99");
		assertCouponError(() -> redisIssuer.issue(couponId, 99L), CouponErrorCode.COUPON_ALREADY_ISSUED);
		assertCouponError(() -> redisIssuer.issue(couponId, 8L), CouponErrorCode.COUPON_QUANTITY_EXHAUSTED);

		long size = redisIssuer.reconcile(couponId);

		assertThat(size).isEqualTo(1);
		assertThat(redisTemplate.opsForSet().members(key)).containsExactly("7");
		assertThat(redisTemplate.getExpire(key)).as("맞춘 뒤에도 키 만료 시간이 유지된다").isPositive();
		redisIssuer.issue(couponId, 99L);
		assertThat(issuedQuantity(couponId)).isEqualTo(2);
	}

	@Test
	@DisplayName("[Redis] 발급 기록이 없는 쿠폰을 맞추면 Redis 키를 지운다")
	void reconcileEmpty() {
		Long couponId = createCoupon(2);
		redisTemplate.opsForSet().add(RedisCouponIssuer.issuedUsersKey(couponId), "99");

		assertThat(context.getBean(RedisCouponIssuer.class).reconcile(couponId)).isZero();
		assertThat(redisTemplate.hasKey(RedisCouponIssuer.issuedUsersKey(couponId))).isFalse();
	}
}
