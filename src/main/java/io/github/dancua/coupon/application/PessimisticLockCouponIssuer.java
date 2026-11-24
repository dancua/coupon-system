package io.github.dancua.coupon.application;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.dancua.coupon.domain.Coupon;
import io.github.dancua.coupon.domain.CouponErrorCode;
import io.github.dancua.coupon.domain.CouponException;
import io.github.dancua.coupon.infrastructure.CouponJpaRepository;
import lombok.RequiredArgsConstructor;

/**
 * 비관적 락: 쿠폰 행을 SELECT ... FOR UPDATE로 잠그고 발급한다.
 * <p>
 * 장점: 구현이 단순하고 정확하다. 단점: 같은 쿠폰에 대한 모든 요청이 한 행 앞에 줄을 서므로
 * 처리량이 DB 커넥션 수와 트랜잭션 시간에 묶인다.
 */
@Component
@RequiredArgsConstructor
public class PessimisticLockCouponIssuer implements CouponIssuer {

	private final CouponJpaRepository couponRepository;
	private final CouponIssueRecorder recorder;
	private final Clock clock;

	@Override
	@Transactional
	public Long issue(Long couponId, Long userId) {
		Coupon coupon = couponRepository.findByIdForUpdate(couponId)
			.orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
		return recorder.record(coupon, userId, LocalDateTime.now(clock));
	}
}
