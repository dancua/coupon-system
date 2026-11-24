package io.github.dancua.coupon.api;

import java.time.LocalDateTime;

import io.github.dancua.coupon.domain.Coupon;
import io.github.dancua.coupon.domain.DiscountType;

public record CouponResponse(
	Long id,
	String name,
	DiscountType discountType,
	int discountValue,
	int totalQuantity,
	int issuedQuantity,
	int remainingQuantity,
	LocalDateTime issueStartAt,
	LocalDateTime issueEndAt,
	LocalDateTime expiresAt
) {

	public static CouponResponse from(Coupon coupon) {
		return new CouponResponse(coupon.getId(), coupon.getName(), coupon.getDiscountType(),
			coupon.getDiscountValue(), coupon.getTotalQuantity(), coupon.getIssuedQuantity(),
			coupon.getRemainingQuantity(), coupon.getIssueStartAt(), coupon.getIssueEndAt(), coupon.getExpiresAt());
	}
}
