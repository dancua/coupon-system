package io.github.dancua.coupon.domain;

import lombok.Getter;

@Getter
public class CouponException extends RuntimeException {

	private final CouponErrorCode errorCode;

	public CouponException(CouponErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public CouponException(CouponErrorCode errorCode, String detail) {
		super(errorCode.getMessage() + " (" + detail + ")");
		this.errorCode = errorCode;
	}
}
