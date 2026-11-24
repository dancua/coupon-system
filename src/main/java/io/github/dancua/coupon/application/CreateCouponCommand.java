package io.github.dancua.coupon.application;

import java.time.LocalDateTime;

import io.github.dancua.coupon.domain.DiscountType;

public record CreateCouponCommand(
	String name,
	DiscountType discountType,
	int discountValue,
	int totalQuantity,
	LocalDateTime issueStartAt,
	LocalDateTime issueEndAt,
	LocalDateTime expiresAt
) {
}
