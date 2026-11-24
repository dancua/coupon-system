package io.github.dancua.coupon.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import io.github.dancua.coupon.domain.Coupon;
import io.github.dancua.coupon.domain.CouponErrorCode;
import io.github.dancua.coupon.domain.CouponException;
import io.github.dancua.coupon.domain.DiscountType;
import io.github.dancua.coupon.domain.IssuedCoupon;
import io.github.dancua.coupon.domain.IssuedCouponStatus;
import io.github.dancua.coupon.infrastructure.CouponJpaRepository;
import io.github.dancua.coupon.infrastructure.IssuedCouponJpaRepository;

@SpringBootTest
@ActiveProfiles("test")
class CouponServiceTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	/** 테스트 시계의 현재 시각: 2026-12-01 11:00 */
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 1, 11, 0);

	@TestConfiguration
	static class FixedClockConfig {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
		}
	}

	@Autowired
	private CouponService couponService;

	@Autowired
	private CouponJpaRepository couponRepository;

	@Autowired
	private IssuedCouponJpaRepository issuedCouponRepository;

	@AfterEach
	void tearDown() {
		issuedCouponRepository.deleteAllInBatch();
		couponRepository.deleteAllInBatch();
	}

	private Long createCoupon(int totalQuantity) {
		return couponService.createCoupon(new CreateCouponCommand(
			"선착순 3000원 할인", DiscountType.FIXED, 3000, totalQuantity,
			NOW.minusHours(1), NOW.plusHours(1), NOW.plusDays(30)));
	}

	private static void assertCouponError(Runnable action, CouponErrorCode expected) {
		assertThatThrownBy(action::run)
			.isInstanceOf(CouponException.class)
			.extracting("errorCode")
			.isEqualTo(expected);
	}

	@Nested
	@DisplayName("쿠폰 발급")
	class Issue {

		@Test
		@DisplayName("발급하면 발급 쿠폰이 저장되고 쿠폰의 발급 수량이 1 늘어난다")
		void issue() {
			Long couponId = createCoupon(10);

			Long issuedCouponId = couponService.issue(couponId, 7L);

			IssuedCoupon issued = issuedCouponRepository.findById(issuedCouponId).orElseThrow();
			assertThat(issued.getUserId()).isEqualTo(7L);
			assertThat(issued.getIssuedAt()).isEqualTo(NOW);
			assertThat(issued.getExpiresAt()).isEqualTo(NOW.plusDays(30));
			assertThat(couponRepository.findById(couponId).orElseThrow().getIssuedQuantity()).isEqualTo(1);
		}

		@Test
		@DisplayName("없는 쿠폰은 발급할 수 없다")
		void notFound() {
			assertCouponError(() -> couponService.issue(999L, 7L), CouponErrorCode.COUPON_NOT_FOUND);
		}

		@Test
		@DisplayName("같은 사용자는 같은 쿠폰을 두 번 받을 수 없고, 발급 수량도 늘지 않는다")
		void alreadyIssued() {
			Long couponId = createCoupon(10);
			couponService.issue(couponId, 7L);

			assertCouponError(() -> couponService.issue(couponId, 7L), CouponErrorCode.COUPON_ALREADY_ISSUED);
			assertThat(couponRepository.findById(couponId).orElseThrow().getIssuedQuantity()).isEqualTo(1);
		}

		@Test
		@DisplayName("수량이 모두 소진되면 발급할 수 없다")
		void exhausted() {
			Long couponId = createCoupon(1);
			couponService.issue(couponId, 7L);

			assertCouponError(() -> couponService.issue(couponId, 8L), CouponErrorCode.COUPON_QUANTITY_EXHAUSTED);
			assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1);
		}

		@Test
		@DisplayName("발급 기간이 지난 쿠폰은 발급할 수 없다")
		void notInPeriod() {
			Long couponId = couponService.createCoupon(new CreateCouponCommand(
				"지난 쿠폰", DiscountType.FIXED, 1000, 10,
				NOW.minusDays(2), NOW.minusDays(1), NOW.plusDays(30)));

			assertCouponError(() -> couponService.issue(couponId, 7L), CouponErrorCode.COUPON_NOT_IN_ISSUE_PERIOD);
		}
	}

	@Nested
	@DisplayName("내 쿠폰 조회")
	class MyCoupons {

		@Test
		@DisplayName("사용자가 발급받은 쿠폰만 조회된다")
		void findMine() {
			Long couponId = createCoupon(10);
			couponService.issue(couponId, 7L);
			couponService.issue(couponId, 8L);

			List<IssuedCoupon> mine = couponService.getIssuedCoupons(7L);

			assertThat(mine).extracting(IssuedCoupon::getUserId).containsExactly(7L);
		}
	}

	@Nested
	@DisplayName("쿠폰 사용과 취소")
	class UseAndCancel {

		@Test
		@DisplayName("발급받은 쿠폰을 사용하면 USED가 되고, 취소하면 ISSUED로 돌아간다")
		void useAndCancel() {
			Long issuedCouponId = couponService.issue(createCoupon(10), 7L);

			couponService.use(issuedCouponId, 7L);
			assertThat(issuedCouponRepository.findById(issuedCouponId).orElseThrow().getStatus())
				.isEqualTo(IssuedCouponStatus.USED);

			couponService.cancelUse(issuedCouponId, 7L);
			assertThat(issuedCouponRepository.findById(issuedCouponId).orElseThrow().getStatus())
				.isEqualTo(IssuedCouponStatus.ISSUED);
		}

		@Test
		@DisplayName("다른 사람의 쿠폰은 사용할 수 없다 (존재 여부를 숨기기 위해 NOT_FOUND)")
		void otherUsersCoupon() {
			Long issuedCouponId = couponService.issue(createCoupon(10), 7L);

			assertCouponError(() -> couponService.use(issuedCouponId, 8L), CouponErrorCode.ISSUED_COUPON_NOT_FOUND);
		}
	}

	@Test
	@DisplayName("잘못된 정책으로는 쿠폰을 만들 수 없다")
	void invalidPolicy() {
		assertCouponError(() -> couponService.createCoupon(new CreateCouponCommand(
			"잘못된 쿠폰", DiscountType.RATE, 150, 10,
			NOW, NOW.plusHours(1), NOW.plusDays(1))), CouponErrorCode.INVALID_COUPON_POLICY);
		assertThat(couponRepository.count()).isZero();
	}

	@Test
	@DisplayName("Clock 빈은 테스트에서 고정 시각으로 교체된다")
	void clockIsFixed(@Autowired Clock clock) {
		assertThat(Instant.now(clock)).isEqualTo(NOW.atZone(ZONE).toInstant());
	}
}
