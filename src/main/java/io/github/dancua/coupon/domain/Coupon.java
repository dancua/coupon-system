package io.github.dancua.coupon.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 쿠폰 정책. 발급 가능 기간은 [issueStartAt, issueEndAt) 이다.
 */
@Entity
@Table(name = "coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

	private static final int MAX_RATE = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DiscountType discountType;

	@Column(nullable = false)
	private int discountValue;

	@Column(nullable = false)
	private int totalQuantity;

	@Column(nullable = false)
	private int issuedQuantity;

	@Column(nullable = false)
	private LocalDateTime issueStartAt;

	@Column(nullable = false)
	private LocalDateTime issueEndAt;

	/** 발급된 쿠폰의 사용 만료 시각 */
	@Column(nullable = false)
	private LocalDateTime expiresAt;

	private Coupon(String name, DiscountType discountType, int discountValue, int totalQuantity,
		LocalDateTime issueStartAt, LocalDateTime issueEndAt, LocalDateTime expiresAt) {
		this.name = name;
		this.discountType = discountType;
		this.discountValue = discountValue;
		this.totalQuantity = totalQuantity;
		this.issuedQuantity = 0;
		this.issueStartAt = issueStartAt;
		this.issueEndAt = issueEndAt;
		this.expiresAt = expiresAt;
	}

	public static Coupon create(String name, DiscountType discountType, int discountValue, int totalQuantity,
		LocalDateTime issueStartAt, LocalDateTime issueEndAt, LocalDateTime expiresAt) {
		validatePolicy(name, discountType, discountValue, totalQuantity, issueStartAt, issueEndAt, expiresAt);
		return new Coupon(name, discountType, discountValue, totalQuantity, issueStartAt, issueEndAt, expiresAt);
	}

	private static void validatePolicy(String name, DiscountType discountType, int discountValue,
		int totalQuantity, LocalDateTime issueStartAt, LocalDateTime issueEndAt, LocalDateTime expiresAt) {
		if (name == null || name.isBlank()) {
			throw new CouponException(CouponErrorCode.INVALID_COUPON_POLICY, "이름은 필수입니다");
		}
		if (discountType == null || issueStartAt == null || issueEndAt == null || expiresAt == null) {
			throw new CouponException(CouponErrorCode.INVALID_COUPON_POLICY, "할인 종류와 기간은 필수입니다");
		}
		if (totalQuantity <= 0) {
			throw new CouponException(CouponErrorCode.INVALID_COUPON_POLICY, "총 수량은 1 이상이어야 합니다");
		}
		if (discountValue <= 0) {
			throw new CouponException(CouponErrorCode.INVALID_COUPON_POLICY, "할인 값은 1 이상이어야 합니다");
		}
		if (discountType == DiscountType.RATE && discountValue > MAX_RATE) {
			throw new CouponException(CouponErrorCode.INVALID_COUPON_POLICY, "할인율은 100% 이하여야 합니다");
		}
		if (!issueStartAt.isBefore(issueEndAt)) {
			throw new CouponException(CouponErrorCode.INVALID_COUPON_POLICY, "발급 시작은 발급 종료보다 빨라야 합니다");
		}
		if (expiresAt.isBefore(issueEndAt)) {
			throw new CouponException(CouponErrorCode.INVALID_COUPON_POLICY, "사용 만료는 발급 종료 이후여야 합니다");
		}
	}

	/**
	 * 발급 수량을 1 늘린다. 동시성 제어는 이 메서드가 아니라 호출하는 쪽(락, Redis)에서 담당한다.
	 */
	public void issue(LocalDateTime now) {
		if (!isInIssuePeriod(now)) {
			throw new CouponException(CouponErrorCode.COUPON_NOT_IN_ISSUE_PERIOD);
		}
		if (issuedQuantity >= totalQuantity) {
			throw new CouponException(CouponErrorCode.COUPON_QUANTITY_EXHAUSTED);
		}
		issuedQuantity++;
	}

	public boolean isInIssuePeriod(LocalDateTime now) {
		return !now.isBefore(issueStartAt) && now.isBefore(issueEndAt);
	}

	public int getRemainingQuantity() {
		return totalQuantity - issuedQuantity;
	}

	/**
	 * 주문 금액에 대한 할인 금액. 할인 금액은 주문 금액을 넘지 않으며, 정률 할인의 원 단위 미만은 버린다.
	 */
	public long calculateDiscount(long orderAmount) {
		if (orderAmount < 0) {
			throw new IllegalArgumentException("주문 금액은 0 이상이어야 합니다: " + orderAmount);
		}
		long discount = switch (discountType) {
			case FIXED -> discountValue;
			case RATE -> orderAmount * discountValue / MAX_RATE;
		};
		return Math.min(discount, orderAmount);
	}
}
