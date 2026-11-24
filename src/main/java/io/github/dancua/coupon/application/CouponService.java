package io.github.dancua.coupon.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.dancua.coupon.domain.Coupon;
import io.github.dancua.coupon.domain.CouponErrorCode;
import io.github.dancua.coupon.domain.CouponException;
import io.github.dancua.coupon.domain.IssuedCoupon;
import io.github.dancua.coupon.infrastructure.CouponJpaRepository;
import io.github.dancua.coupon.infrastructure.IssuedCouponJpaRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

	private final CouponJpaRepository couponRepository;
	private final IssuedCouponJpaRepository issuedCouponRepository;
	private final Clock clock;

	@Transactional
	public Long createCoupon(CreateCouponCommand command) {
		Coupon coupon = Coupon.create(command.name(), command.discountType(), command.discountValue(),
			command.totalQuantity(), command.issueStartAt(), command.issueEndAt(), command.expiresAt());
		return couponRepository.save(coupon).getId();
	}

	public Coupon getCoupon(Long couponId) {
		return couponRepository.findById(couponId)
			.orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
	}

	public List<IssuedCoupon> getIssuedCoupons(Long userId) {
		return issuedCouponRepository.findAllByUserIdOrderByIssuedAtDesc(userId);
	}

	@Transactional
	public void use(Long issuedCouponId, Long userId) {
		findOwnedIssuedCoupon(issuedCouponId, userId).use(LocalDateTime.now(clock));
	}

	@Transactional
	public void cancelUse(Long issuedCouponId, Long userId) {
		findOwnedIssuedCoupon(issuedCouponId, userId).cancelUse();
	}

	/** 다른 사람의 쿠폰이면 존재 여부를 노출하지 않도록 NOT_FOUND로 응답한다. */
	private IssuedCoupon findOwnedIssuedCoupon(Long issuedCouponId, Long userId) {
		return issuedCouponRepository.findById(issuedCouponId)
			.filter(issued -> issued.getUserId().equals(userId))
			.orElseThrow(() -> new CouponException(CouponErrorCode.ISSUED_COUPON_NOT_FOUND));
	}
}
