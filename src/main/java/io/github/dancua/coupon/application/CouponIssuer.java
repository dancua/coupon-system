package io.github.dancua.coupon.application;

/**
 * 쿠폰 발급 전략. 동시 요청에서 총 수량과 1인 1매를 지키는 방법만 구현체마다 다르다.
 * <ul>
 *   <li>{@link PessimisticLockCouponIssuer} - DB 행 락(SELECT ... FOR UPDATE)</li>
 *   <li>{@link OptimisticLockCouponIssuer} - @Version 충돌 감지 + 재시도</li>
 *   <li>{@link RedisCouponIssuer} - Redis Lua 스크립트로 원자적 선점 (API 기본값)</li>
 * </ul>
 */
public interface CouponIssuer {

	/**
	 * @return 발급된 쿠폰(IssuedCoupon) id
	 */
	Long issue(Long couponId, Long userId);
}
