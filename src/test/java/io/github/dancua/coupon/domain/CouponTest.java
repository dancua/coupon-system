package io.github.dancua.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CouponTest {

	private static final LocalDateTime ISSUE_START = LocalDateTime.of(2026, 12, 1, 10, 0);
	private static final LocalDateTime ISSUE_END = LocalDateTime.of(2026, 12, 2, 10, 0);
	private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 12, 31, 23, 59);

	private static Coupon coupon(DiscountType type, int value, int totalQuantity) {
		return Coupon.create("겨울 할인 쿠폰", type, value, totalQuantity, ISSUE_START, ISSUE_END, EXPIRES_AT);
	}

	private static void assertCouponError(Runnable action, CouponErrorCode expected) {
		assertThatThrownBy(action::run)
			.isInstanceOf(CouponException.class)
			.extracting("errorCode")
			.isEqualTo(expected);
	}

	@Nested
	@DisplayName("쿠폰 생성")
	class Create {

		@Test
		@DisplayName("정상 생성 시 발급 수량은 0이다")
		void createsWithZeroIssued() {
			Coupon coupon = coupon(DiscountType.FIXED, 3000, 100);

			assertThat(coupon.getIssuedQuantity()).isZero();
			assertThat(coupon.getTotalQuantity()).isEqualTo(100);
			assertThat(coupon.getRemainingQuantity()).isEqualTo(100);
		}

		@Test
		@DisplayName("이름이 비어 있으면 실패한다")
		void blankName() {
			assertCouponError(
				() -> Coupon.create(" ", DiscountType.FIXED, 3000, 100, ISSUE_START, ISSUE_END, EXPIRES_AT),
				CouponErrorCode.INVALID_COUPON_POLICY);
		}

		@Test
		@DisplayName("총 수량이 0 이하면 실패한다")
		void nonPositiveQuantity() {
			assertCouponError(() -> coupon(DiscountType.FIXED, 3000, 0), CouponErrorCode.INVALID_COUPON_POLICY);
		}

		@Test
		@DisplayName("정액 할인 금액이 0 이하면 실패한다")
		void nonPositiveFixedAmount() {
			assertCouponError(() -> coupon(DiscountType.FIXED, 0, 100), CouponErrorCode.INVALID_COUPON_POLICY);
		}

		@Test
		@DisplayName("정률 할인은 1~100% 범위여야 한다")
		void rateOutOfRange() {
			assertCouponError(() -> coupon(DiscountType.RATE, 0, 100), CouponErrorCode.INVALID_COUPON_POLICY);
			assertCouponError(() -> coupon(DiscountType.RATE, 101, 100), CouponErrorCode.INVALID_COUPON_POLICY);
		}

		@Test
		@DisplayName("발급 시작이 발급 종료보다 늦으면 실패한다")
		void startAfterEnd() {
			assertCouponError(
				() -> Coupon.create("쿠폰", DiscountType.FIXED, 3000, 100, ISSUE_END, ISSUE_START, EXPIRES_AT),
				CouponErrorCode.INVALID_COUPON_POLICY);
		}

		@Test
		@DisplayName("사용 만료가 발급 종료보다 빠르면 실패한다")
		void expiresBeforeIssueEnd() {
			assertCouponError(
				() -> Coupon.create("쿠폰", DiscountType.FIXED, 3000, 100, ISSUE_START, ISSUE_END,
					ISSUE_END.minusMinutes(1)),
				CouponErrorCode.INVALID_COUPON_POLICY);
		}
	}

	@Nested
	@DisplayName("쿠폰 발급")
	class Issue {

		@Test
		@DisplayName("발급 기간 안이면 발급 수량이 1 증가한다")
		void issueIncrementsQuantity() {
			Coupon coupon = coupon(DiscountType.FIXED, 3000, 100);

			coupon.issue(ISSUE_START.plusHours(1));

			assertThat(coupon.getIssuedQuantity()).isEqualTo(1);
			assertThat(coupon.getRemainingQuantity()).isEqualTo(99);
		}

		@Test
		@DisplayName("발급 시작 시각 정각은 발급 가능하고, 종료 시각 정각은 불가능하다")
		void periodBoundaries() {
			Coupon coupon = coupon(DiscountType.FIXED, 3000, 100);

			coupon.issue(ISSUE_START);

			assertCouponError(() -> coupon.issue(ISSUE_END), CouponErrorCode.COUPON_NOT_IN_ISSUE_PERIOD);
		}

		@Test
		@DisplayName("발급 시작 전에는 발급할 수 없다")
		void beforeStart() {
			Coupon coupon = coupon(DiscountType.FIXED, 3000, 100);

			assertCouponError(() -> coupon.issue(ISSUE_START.minusSeconds(1)),
				CouponErrorCode.COUPON_NOT_IN_ISSUE_PERIOD);
		}

		@Test
		@DisplayName("수량이 모두 소진되면 발급할 수 없다")
		void exhausted() {
			Coupon coupon = coupon(DiscountType.FIXED, 3000, 2);
			LocalDateTime now = ISSUE_START.plusMinutes(1);
			coupon.issue(now);
			coupon.issue(now);

			assertCouponError(() -> coupon.issue(now), CouponErrorCode.COUPON_QUANTITY_EXHAUSTED);
			assertThat(coupon.getIssuedQuantity()).isEqualTo(2);
		}

		@Test
		@DisplayName("발급 가능 검증은 기간 밖이면 실패하고, 기간 안이면 수량을 바꾸지 않고 통과한다")
		void validateIssuable() {
			Coupon coupon = coupon(DiscountType.FIXED, 3000, 100);

			assertCouponError(() -> coupon.validateIssuable(ISSUE_END), CouponErrorCode.COUPON_NOT_IN_ISSUE_PERIOD);
			coupon.validateIssuable(ISSUE_START);

			assertThat(coupon.getIssuedQuantity()).isZero();
		}
	}

	@Nested
	@DisplayName("할인 금액 계산")
	class Discount {

		@Test
		@DisplayName("정액 할인은 할인 금액만큼 깎는다")
		void fixed() {
			assertThat(coupon(DiscountType.FIXED, 3000, 1).calculateDiscount(10_000)).isEqualTo(3000);
		}

		@Test
		@DisplayName("정액 할인은 주문 금액을 넘지 않는다")
		void fixedCappedByOrderAmount() {
			assertThat(coupon(DiscountType.FIXED, 3000, 1).calculateDiscount(2000)).isEqualTo(2000);
		}

		@Test
		@DisplayName("정률 할인은 비율만큼 깎고 원 단위 미만은 버린다")
		void rate() {
			Coupon coupon = coupon(DiscountType.RATE, 15, 1);

			assertThat(coupon.calculateDiscount(10_000)).isEqualTo(1500);
			assertThat(coupon.calculateDiscount(999)).isEqualTo(149);
		}

		@Test
		@DisplayName("주문 금액이 음수면 실패한다")
		void negativeOrderAmount() {
			assertThatThrownBy(() -> coupon(DiscountType.FIXED, 3000, 1).calculateDiscount(-1))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}
}
