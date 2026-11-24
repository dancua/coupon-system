package io.github.dancua.coupon.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.dancua.coupon.domain.CouponErrorCode;
import io.github.dancua.coupon.domain.DiscountType;
import io.github.dancua.coupon.domain.IssuedCoupon;
import io.github.dancua.coupon.domain.IssuedCouponStatus;
import io.github.dancua.coupon.support.IntegrationTestSupport;

class CouponServiceTest extends IntegrationTestSupport {

	@Autowired
	private CouponIssuer couponIssuer;

	@Nested
	@DisplayName("내 쿠폰 조회")
	class MyCoupons {

		@Test
		@DisplayName("사용자가 발급받은 쿠폰만 조회된다")
		void findMine() {
			Long couponId = createCoupon(10);
			couponIssuer.issue(couponId, 7L);
			couponIssuer.issue(couponId, 8L);

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
			Long issuedCouponId = couponIssuer.issue(createCoupon(10), 7L);

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
			Long issuedCouponId = couponIssuer.issue(createCoupon(10), 7L);

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
