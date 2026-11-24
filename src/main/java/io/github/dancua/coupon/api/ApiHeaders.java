package io.github.dancua.coupon.api;

/**
 * 인증을 붙이기 전까지 사용자 식별은 요청 헤더로 받는다.
 */
public final class ApiHeaders {

	public static final String USER_ID = "X-User-Id";

	private ApiHeaders() {
	}
}
