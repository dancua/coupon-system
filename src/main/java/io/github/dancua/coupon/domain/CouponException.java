package io.github.dancua.coupon.domain;

import lombok.Getter;

/**
 * 사용자 요청이 쿠폰 규칙을 어겼을 때 던진다. api 계층이 {@link CouponErrorCode}별 HTTP 상태(4xx 등)로 바꿔 응답한다.
 * <p>
 * 예외 선택 기준:
 * <ul>
 *   <li>사용자 입력이나 현재 상태 때문에 거절해야 하는 경우 → {@code CouponException} (예: 잘못된 정책, 소진, 이미 사용)</li>
 *   <li>호출하는 코드가 계약을 어긴 경우(버그) → {@link IllegalArgumentException} / {@link IllegalStateException}.
 *       사용자가 고칠 수 없는 문제이므로 500으로 응답하고 로그를 남긴다 (예: 내부에서 null id를 넘김, 음수 주문 금액)</li>
 * </ul>
 */
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
