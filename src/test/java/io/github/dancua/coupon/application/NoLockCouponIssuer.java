package io.github.dancua.coupon.application;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.transaction.annotation.Transactional;

import io.github.dancua.coupon.domain.Coupon;
import io.github.dancua.coupon.domain.CouponErrorCode;
import io.github.dancua.coupon.domain.CouponException;
import jakarta.persistence.EntityManager;

/**
 * 동시성 제어가 없는 발급 (테스트 전용, 초과 발급 재현용 "Before").
 * <p>
 * Phase 3의 발급 코드와 같은 방식으로 동작한다. 쿠폰을 읽고, 메모리에서 +1 한 값을 그대로 덮어쓴다.
 * 동시에 들어온 요청들이 같은 값을 읽으면 갱신이 유실되어 총 수량보다 많이 발급된다.
 * <p>
 * Phase 4에서 Coupon에 @Version이 생겨 엔티티를 그냥 저장하면 낙관적 락이 걸려 버린다. 그래서 엔티티를 분리(detach)한 뒤
 * 버전 확인이 없는 UPDATE로 값을 직접 덮어써 "락 없음"을 재현한다. 운영 코드에서 쓰면 안 되므로 테스트 소스에만 둔다.
 */
public class NoLockCouponIssuer implements CouponIssuer {

	private final EntityManager entityManager;
	private final CouponIssueRecorder recorder;
	private final Clock clock;

	public NoLockCouponIssuer(EntityManager entityManager, CouponIssueRecorder recorder, Clock clock) {
		this.entityManager = entityManager;
		this.recorder = recorder;
		this.clock = clock;
	}

	@Override
	@Transactional
	public Long issue(Long couponId, Long userId) {
		Coupon coupon = entityManager.find(Coupon.class, couponId);
		if (coupon == null) {
			throw new CouponException(CouponErrorCode.COUPON_NOT_FOUND);
		}
		entityManager.detach(coupon);

		Long issuedCouponId = recorder.record(coupon, userId, LocalDateTime.now(clock));

		// 읽어 둔(오래됐을 수 있는) 값 + 1을 확인 없이 덮어쓴다 → 갱신 유실
		entityManager.createQuery("update Coupon c set c.issuedQuantity = :quantity where c.id = :id")
			.setParameter("quantity", coupon.getIssuedQuantity())
			.setParameter("id", couponId)
			.executeUpdate();
		return issuedCouponId;
	}
}
