package io.github.dancua.coupon.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
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

	/**
	 * 쿠폰을 발급한다.
	 * <p>
	 * 주의: 아직 동시성 제어가 없다. Coupon을 읽고(issuedQuantity) 메모리에서 +1 한 뒤 저장하므로,
	 * 동시에 요청이 오면 같은 값을 읽어 수량 갱신이 유실되고 총 수량보다 많이 발급된다(Phase 4에서 해결).
	 */
	@Transactional
	public Long issue(Long couponId, Long userId) {
		LocalDateTime now = LocalDateTime.now(clock);
		Coupon coupon = couponRepository.findById(couponId)
			.orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

		if (issuedCouponRepository.existsByCouponIdAndUserId(couponId, userId)) {
			throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
		}
		coupon.issue(now);

		try {
			IssuedCoupon issued = IssuedCoupon.issue(couponId, userId, now, coupon.getExpiresAt());
			return issuedCouponRepository.saveAndFlush(issued).getId();
		} catch (DataIntegrityViolationException e) {
			// exists 확인과 저장 사이에 같은 사용자의 요청이 먼저 저장된 경우: unique 제약이 막아 준다
			throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
		}
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
