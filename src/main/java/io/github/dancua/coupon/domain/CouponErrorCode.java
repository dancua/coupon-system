package io.github.dancua.coupon.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 쿠폰 도메인 규칙 위반 사유. HTTP 상태 매핑은 api 계층에서 담당한다.
 */
@Getter
@RequiredArgsConstructor
public enum CouponErrorCode {

	INVALID_COUPON_POLICY("쿠폰 정책이 올바르지 않습니다."),
	COUPON_NOT_FOUND("쿠폰을 찾을 수 없습니다."),
	ISSUED_COUPON_NOT_FOUND("발급받은 쿠폰을 찾을 수 없습니다."),
	COUPON_ALREADY_ISSUED("이미 발급받은 쿠폰입니다."),
	COUPON_NOT_IN_ISSUE_PERIOD("쿠폰 발급 기간이 아닙니다."),
	COUPON_QUANTITY_EXHAUSTED("쿠폰이 모두 소진되었습니다."),
	COUPON_ISSUE_BUSY("요청이 많아 발급하지 못했습니다. 잠시 후 다시 시도해 주세요."),
	COUPON_ALREADY_USED("이미 사용한 쿠폰입니다."),
	COUPON_EXPIRED("만료된 쿠폰입니다."),
	COUPON_NOT_USED("사용하지 않은 쿠폰은 취소할 수 없습니다.");

	private final String message;
}
