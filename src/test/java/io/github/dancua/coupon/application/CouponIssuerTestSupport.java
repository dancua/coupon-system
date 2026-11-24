package io.github.dancua.coupon.application;

import java.util.stream.Stream;

import io.github.dancua.coupon.support.IntegrationTestSupport;

/**
 * 발급 전략 비교 테스트의 공통 부모. 컨텍스트 설정은 {@link IntegrationTestSupport}를 그대로 쓴다.
 */
abstract class CouponIssuerTestSupport extends IntegrationTestSupport {

	/**
	 * 정확성을 보장해야 하는 비교 대상 전략. @MethodSource("issuers")로 각 테스트를 세 번씩 실행한다.
	 * 일부러 동시성 제어를 뺀 {@link NoLockCouponIssuer}는 여기에 넣지 않는다(초과 발급 재현 전용).
	 */
	static Stream<Class<? extends CouponIssuer>> issuers() {
		return Stream.of(PessimisticLockCouponIssuer.class, OptimisticLockCouponIssuer.class,
			RedisCouponIssuer.class);
	}

	CouponIssuer issuer(Class<? extends CouponIssuer> type) {
		return context.getBean(type);
	}
}
