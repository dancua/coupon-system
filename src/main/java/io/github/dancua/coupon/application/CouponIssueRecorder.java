package io.github.dancua.coupon.application;

import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import io.github.dancua.coupon.domain.Coupon;
import io.github.dancua.coupon.domain.CouponErrorCode;
import io.github.dancua.coupon.domain.CouponException;
import io.github.dancua.coupon.domain.IssuedCoupon;
import io.github.dancua.coupon.infrastructure.IssuedCouponJpaRepository;
import lombok.RequiredArgsConstructor;

/**
 * 락으로 보호된 Coupon을 받아 발급 규칙을 적용하고 발급 쿠폰을 저장한다.
 * 트랜잭션을 직접 열지 않고 호출한 쪽(락 전략)의 트랜잭션에 참여한다.
 */
@Component
@RequiredArgsConstructor
class CouponIssueRecorder {

	private final IssuedCouponJpaRepository issuedCouponRepository;

	Long record(Coupon coupon, Long userId, LocalDateTime now) {
		if (issuedCouponRepository.existsByCouponIdAndUserId(coupon.getId(), userId)) {
			throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
		}
		coupon.issue(now);
		try {
			IssuedCoupon issued = IssuedCoupon.issue(coupon.getId(), userId, now, coupon.getExpiresAt());
			return issuedCouponRepository.saveAndFlush(issued).getId();
		} catch (DataIntegrityViolationException e) {
			// exists 확인과 저장 사이에 같은 사용자의 요청이 먼저 저장된 경우: unique 제약이 최종 방어선
			throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
		}
	}
}
