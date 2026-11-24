package io.github.dancua.coupon.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.dancua.coupon.domain.Coupon;
import io.github.dancua.coupon.domain.CouponErrorCode;
import io.github.dancua.coupon.domain.CouponException;
import io.github.dancua.coupon.domain.IssuedCoupon;
import io.github.dancua.coupon.infrastructure.CouponJpaRepository;
import io.github.dancua.coupon.infrastructure.IssuedCouponJpaRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 선점 방식 (API 기본 전략).
 * <ol>
 *   <li>Lua 스크립트로 "이미 받았나? / 남았나? / 받은 사람 목록에 추가"를 한 번에(원자적으로) 처리한다.
 *       DB 락 없이 메모리에서 끝나므로 요청이 몰려도 빠르다.</li>
 *   <li>선점에 성공한 요청만 DB에 저장한다. 따라서 DB에는 최대 총 수량만큼의 쓰기만 도달한다.</li>
 *   <li>DB 저장이 실패하면 Redis에서 사용자를 빼서(보상) 자리를 돌려준다.</li>
 * </ol>
 * DB의 조건부 UPDATE(issuedQuantity &lt; totalQuantity)와 unique 제약은 Redis 데이터가 유실됐을 때의 최종 방어선이다.
 */
@Slf4j
@Primary
@Component
public class RedisCouponIssuer implements CouponIssuer {

	private static final RedisScript<Long> ISSUE_SCRIPT =
		RedisScript.of(new ClassPathResource("redis/issue-coupon.lua"), Long.class);
	private static final long RESERVED = 1L;
	private static final long DUPLICATED = 2L;
	private static final long EXHAUSTED = 3L;
	private static final Duration KEY_RETENTION_AFTER_ISSUE_END = Duration.ofDays(1);

	private final StringRedisTemplate redisTemplate;
	private final CouponJpaRepository couponRepository;
	private final IssuedCouponJpaRepository issuedCouponRepository;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	RedisCouponIssuer(StringRedisTemplate redisTemplate, CouponJpaRepository couponRepository,
		IssuedCouponJpaRepository issuedCouponRepository, PlatformTransactionManager transactionManager,
		Clock clock) {
		this.redisTemplate = redisTemplate;
		this.couponRepository = couponRepository;
		this.issuedCouponRepository = issuedCouponRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	static String issuedUsersKey(Long couponId) {
		return "coupon:" + couponId + ":issued-users";
	}

	@Override
	public Long issue(Long couponId, Long userId) {
		LocalDateTime now = LocalDateTime.now(clock);
		Coupon coupon = couponRepository.findById(couponId)
			.orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
		coupon.validateIssuable(now);

		String key = issuedUsersKey(couponId);
		reserve(key, userId, coupon, now);
		try {
			return transactionTemplate.execute(status -> save(coupon, userId, now));
		} catch (RuntimeException e) {
			// DB 저장 실패 → Redis 선점을 되돌려 다른 사용자가 그 자리를 받을 수 있게 한다
			compensate(key, userId, e);
			throw e;
		}
	}

	/**
	 * DB의 발급 기록을 기준으로 Redis의 발급 사용자 Set을 다시 만든다.
	 * <p>
	 * 선점 직후 서버가 죽거나 보상(SREM)마저 실패하면, Redis에는 DB에 없는 "유령 사용자"가 남는다.
	 * 유령은 수량을 차지해 총 수량보다 적게 발급되게 하고, 그 사용자는 영영 발급받지 못한다. 이 메서드가 그것을 바로잡는다.
	 * <p>
	 * 새 Set을 임시 키에 만든 뒤 RENAME으로 한 번에 바꿔치기하므로, 다른 요청이 반쯤 만들어진 Set을 보는 일은 없다.
	 * 바꿔치기 순간 진행 중이던 선점이 Set에서 빠질 수는 있지만, 그 경우에도 DB 조건부 UPDATE와 unique 제약이
	 * 초과 발급과 중복 발급을 막는다(그 요청은 실패 후 보상된다).
	 *
	 * @return 맞춘 뒤 Redis Set의 크기 (= DB 발급 행 수)
	 */
	public long reconcile(Long couponId) {
		Coupon coupon = couponRepository.findById(couponId)
			.orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
		String key = issuedUsersKey(couponId);
		List<Long> userIds = issuedCouponRepository.findUserIdsByCouponId(couponId);
		long ttlSeconds = keyTtlSeconds(coupon, LocalDateTime.now(clock));

		if (userIds.isEmpty() || ttlSeconds <= 0) {
			redisTemplate.delete(key);
			return 0;
		}
		String tempKey = key + ":rebuild:" + UUID.randomUUID();
		redisTemplate.opsForSet().add(tempKey, userIds.stream().map(String::valueOf).toArray(String[]::new));
		redisTemplate.expire(tempKey, Duration.ofSeconds(ttlSeconds));
		redisTemplate.rename(tempKey, key);
		log.info("Redis 발급 사용자 Set을 DB 기준으로 맞췄습니다. couponId={}, size={}", couponId, userIds.size());
		return userIds.size();
	}

	/**
	 * 보상이 실패해도 원래 예외(DB 실패 사유)를 가리지 않는다. 남은 유령은 {@link #reconcile(Long)}이 정리한다.
	 */
	private void compensate(String key, Long userId, RuntimeException cause) {
		try {
			redisTemplate.opsForSet().remove(key, userId.toString());
		} catch (RuntimeException compensationFailure) {
			cause.addSuppressed(compensationFailure);
			log.error("Redis 선점 보상 실패: 유령 사용자가 남았습니다. reconcile이 필요합니다. key={}, userId={}",
				key, userId, compensationFailure);
		}
	}

	/** 발급 종료 후 하루 동안 키를 보관한다. 이미 그 시각이 지났으면 0 이하를 돌려준다. */
	private static long keyTtlSeconds(Coupon coupon, LocalDateTime now) {
		return Duration.between(now, coupon.getIssueEndAt())
			.plus(KEY_RETENTION_AFTER_ISSUE_END)
			.toSeconds();
	}

	private void reserve(String key, Long userId, Coupon coupon, LocalDateTime now) {
		long ttlSeconds = keyTtlSeconds(coupon, now);
		Long result = redisTemplate.execute(ISSUE_SCRIPT, List.of(key),
			userId.toString(), String.valueOf(coupon.getTotalQuantity()), String.valueOf(ttlSeconds));

		if (result == null) {
			throw new IllegalStateException("Redis 발급 스크립트가 결과를 반환하지 않았습니다");
		}
		if (result == DUPLICATED) {
			throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
		}
		if (result == EXHAUSTED) {
			throw new CouponException(CouponErrorCode.COUPON_QUANTITY_EXHAUSTED);
		}
		if (result != RESERVED) {
			throw new IllegalStateException("알 수 없는 Redis 발급 결과: " + result);
		}
	}

	private Long save(Coupon coupon, Long userId, LocalDateTime now) {
		if (couponRepository.increaseIssuedQuantity(coupon.getId()) == 0) {
			throw new CouponException(CouponErrorCode.COUPON_QUANTITY_EXHAUSTED);
		}
		try {
			IssuedCoupon issued = IssuedCoupon.issue(coupon.getId(), userId, now, coupon.getExpiresAt());
			return issuedCouponRepository.saveAndFlush(issued).getId();
		} catch (DataIntegrityViolationException e) {
			throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
		}
	}
}
