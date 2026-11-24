package io.github.dancua.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IssuedCouponTest {

	private static final Long COUPON_ID = 1L;
	private static final Long USER_ID = 7L;
	private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 12, 1, 10, 0);
	private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 12, 31, 23, 59);

	private static IssuedCoupon issued() {
		return IssuedCoupon.issue(COUPON_ID, USER_ID, ISSUED_AT, EXPIRES_AT);
	}

	private static void assertCouponError(Runnable action, CouponErrorCode expected) {
		assertThatThrownBy(action::run)
			.isInstanceOf(CouponException.class)
			.extracting("errorCode")
			.isEqualTo(expected);
	}

	@Test
	@DisplayName("발급 직후에는 ISSUED 상태이고 만료 시각을 가진다")
	void issue() {
		IssuedCoupon issued = issued();

		assertThat(issued.getCouponId()).isEqualTo(COUPON_ID);
		assertThat(issued.getUserId()).isEqualTo(USER_ID);
		assertThat(issued.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
		assertThat(issued.getIssuedAt()).isEqualTo(ISSUED_AT);
		assertThat(issued.getExpiresAt()).isEqualTo(EXPIRES_AT);
		assertThat(issued.getUsedAt()).isNull();
	}

	@Nested
	@DisplayName("쿠폰 사용")
	class Use {

		@Test
		@DisplayName("만료 전이면 USED 상태가 되고 사용 시각이 기록된다")
		void use() {
			IssuedCoupon issued = issued();
			LocalDateTime now = ISSUED_AT.plusDays(1);

			issued.use(now);

			assertThat(issued.getStatus()).isEqualTo(IssuedCouponStatus.USED);
			assertThat(issued.getUsedAt()).isEqualTo(now);
		}

		@Test
		@DisplayName("이미 사용한 쿠폰은 다시 사용할 수 없다")
		void alreadyUsed() {
			IssuedCoupon issued = issued();
			issued.use(ISSUED_AT.plusDays(1));

			assertCouponError(() -> issued.use(ISSUED_AT.plusDays(2)), CouponErrorCode.COUPON_ALREADY_USED);
		}

		@Test
		@DisplayName("만료 시각 이후에는 사용할 수 없다")
		void expired() {
			IssuedCoupon issued = issued();

			assertCouponError(() -> issued.use(EXPIRES_AT), CouponErrorCode.COUPON_EXPIRED);
		}

		@Test
		@DisplayName("EXPIRED 상태가 된 쿠폰은 사용할 수 없다")
		void expiredStatus() {
			IssuedCoupon issued = issued();
			issued.expire(EXPIRES_AT);

			assertCouponError(() -> issued.use(EXPIRES_AT.minusDays(1)), CouponErrorCode.COUPON_EXPIRED);
		}
	}

	@Nested
	@DisplayName("사용 취소")
	class CancelUse {

		@Test
		@DisplayName("사용한 쿠폰을 취소하면 ISSUED 상태로 돌아간다")
		void cancel() {
			IssuedCoupon issued = issued();
			issued.use(ISSUED_AT.plusDays(1));

			issued.cancelUse();

			assertThat(issued.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
			assertThat(issued.getUsedAt()).isNull();
		}

		@Test
		@DisplayName("사용하지 않은 쿠폰은 취소할 수 없다")
		void notUsed() {
			assertCouponError(() -> issued().cancelUse(), CouponErrorCode.COUPON_NOT_USED);
		}
	}

	@Nested
	@DisplayName("만료 처리")
	class Expire {

		@Test
		@DisplayName("만료 시각이 지난 ISSUED 쿠폰은 EXPIRED가 된다")
		void expire() {
			IssuedCoupon issued = issued();

			issued.expire(EXPIRES_AT);

			assertThat(issued.getStatus()).isEqualTo(IssuedCouponStatus.EXPIRED);
		}

		@Test
		@DisplayName("만료 시각 전에는 상태가 바뀌지 않는다")
		void notYet() {
			IssuedCoupon issued = issued();

			issued.expire(EXPIRES_AT.minusSeconds(1));

			assertThat(issued.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
		}

		@Test
		@DisplayName("이미 사용한 쿠폰은 만료 처리해도 USED로 남는다")
		void usedStaysUsed() {
			IssuedCoupon issued = issued();
			issued.use(ISSUED_AT.plusDays(1));

			issued.expire(EXPIRES_AT);

			assertThat(issued.getStatus()).isEqualTo(IssuedCouponStatus.USED);
		}
	}
}
