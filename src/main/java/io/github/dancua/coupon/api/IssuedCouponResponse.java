package io.github.dancua.coupon.api;

import java.time.LocalDateTime;

import io.github.dancua.coupon.domain.IssuedCoupon;
import io.github.dancua.coupon.domain.IssuedCouponStatus;

public record IssuedCouponResponse(
	Long id,
	Long couponId,
	IssuedCouponStatus status,
	LocalDateTime issuedAt,
	LocalDateTime expiresAt,
	LocalDateTime usedAt
) {

	public static IssuedCouponResponse from(IssuedCoupon issued) {
		return new IssuedCouponResponse(issued.getId(), issued.getCouponId(), issued.getStatus(),
			issued.getIssuedAt(), issued.getExpiresAt(), issued.getUsedAt());
	}
}
