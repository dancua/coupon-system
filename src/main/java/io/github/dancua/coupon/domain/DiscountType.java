package io.github.dancua.coupon.domain;

public enum DiscountType {

	/** 정액 할인: discountValue 원만큼 할인 */
	FIXED,

	/** 정률 할인: discountValue % 만큼 할인 (1~100) */
	RATE
}
