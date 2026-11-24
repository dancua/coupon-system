package io.github.dancua.coupon.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.dancua.coupon.domain.Coupon;
import io.github.dancua.coupon.domain.CouponErrorCode;
import io.github.dancua.coupon.domain.CouponException;
import io.github.dancua.coupon.infrastructure.CouponJpaRepository;

/**
 * 낙관적 락: 락 없이 읽고, 저장할 때 @Version이 바뀌었으면 충돌로 보고 처음부터 다시 시도한다.
 * <p>
 * 장점: 충돌이 드문 곳에서는 대기 없이 빠르다. 단점: 선착순처럼 한 행에 요청이 몰리면
 * 대부분이 충돌해 재시도가 폭증하고, 재시도 횟수를 다 쓰면 실패한다.
 * <p>
 * 재시도는 트랜잭션 바깥에서 해야 하므로(@Transactional 메서드 안에서 다시 시도하면 같은 트랜잭션을 재사용한다)
 * TransactionTemplate으로 매 시도마다 새 트랜잭션을 연다.
 */
@Component
public class OptimisticLockCouponIssuer implements CouponIssuer {

	static final int MAX_ATTEMPTS = 100;
	private static final int MAX_BACKOFF_MILLIS = 20;

	private final CouponJpaRepository couponRepository;
	private final CouponIssueRecorder recorder;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	OptimisticLockCouponIssuer(CouponJpaRepository couponRepository, CouponIssueRecorder recorder,
		PlatformTransactionManager transactionManager, Clock clock) {
		this.couponRepository = couponRepository;
		this.recorder = recorder;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@Override
	public Long issue(Long couponId, Long userId) {
		for (int attempt = 1; ; attempt++) {
			try {
				return transactionTemplate.execute(status -> {
					Coupon coupon = couponRepository.findById(couponId)
						.orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
					return recorder.record(coupon, userId, LocalDateTime.now(clock));
				});
			} catch (OptimisticLockingFailureException e) {
				if (attempt >= MAX_ATTEMPTS) {
					throw new CouponException(CouponErrorCode.COUPON_ISSUE_BUSY);
				}
				backoff();
			}
		}
	}

	/** 충돌한 요청들이 같은 순간에 다시 몰리지 않도록 무작위로 조금 쉬었다가 재시도한다. */
	private static void backoff() {
		try {
			Thread.sleep(ThreadLocalRandom.current().nextInt(1, MAX_BACKOFF_MILLIS + 1));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CouponException(CouponErrorCode.COUPON_ISSUE_BUSY);
		}
	}
}
