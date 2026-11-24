package io.github.dancua.coupon.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongUnaryOperator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.dancua.coupon.domain.CouponException;

/**
 * 동시 발급 요청에서 총 수량과 1인 1매가 지켜지는지, 세 전략을 같은 조건으로 비교한다.
 * <p>
 * 동시성 제어가 없는 {@link NoLockCouponIssuer}(Phase 3 방식)로 초과 발급을 함께 재현해 Before / After를 한 번에 비교한다.
 * (Phase 3 기록: 같은 조건에서 100장짜리 쿠폰이 881장 발급됐다.)
 * 실행: {@code ./gradlew concurrencyTest} (결과 표가 콘솔에 출력된다)
 */
@Tag("concurrency")
class CouponIssueConcurrencyTest extends CouponIssuerTestSupport {

	private static final int TOTAL_QUANTITY = 100;
	private static final int REQUEST_COUNT = 1000;
	private static final int THREAD_POOL_SIZE = 32;
	private static final String NAME = "[{index}] {0}";

	@ParameterizedTest(name = NAME)
	@MethodSource("issuers")
	@DisplayName("서로 다른 사용자 1000명이 동시에 요청해도 정확히 100장만 발급된다")
	void differentUsers(Class<? extends CouponIssuer> type) throws InterruptedException {
		Long couponId = createCoupon(TOTAL_QUANTITY);

		Result result = runConcurrently(issuer(type), couponId, REQUEST_COUNT, i -> i);
		result.print(type, "사용자 1000명 → 100장");

		assertThat(result.success()).as("성공 응답 수").isEqualTo(TOTAL_QUANTITY);
		assertThat(issuedCouponRepository.countByCouponId(couponId)).as("실제 발급 행 수").isEqualTo(TOTAL_QUANTITY);
		assertThat(issuedQuantity(couponId)).as("쿠폰의 발급 수량 카운터").isEqualTo(TOTAL_QUANTITY);
	}

	@ParameterizedTest(name = NAME)
	@MethodSource("issuers")
	@DisplayName("같은 사용자가 100번 동시에 요청해도 1장만 발급된다")
	void sameUser(Class<? extends CouponIssuer> type) throws InterruptedException {
		Long couponId = createCoupon(TOTAL_QUANTITY);

		Result result = runConcurrently(issuer(type), couponId, 100, i -> 7L);
		result.print(type, "같은 사용자 100번 → 1장");

		assertThat(result.success()).isEqualTo(1);
		assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1);
		assertThat(issuedQuantity(couponId)).isEqualTo(1);
	}

	@Test
	@DisplayName("[Before] 동시성 제어가 없으면 같은 조건에서 총 수량보다 많이 발급된다 (갱신 유실 재현)")
	void noLockOverIssues() throws InterruptedException {
		Long couponId = createCoupon(TOTAL_QUANTITY);

		Result result = runConcurrently(issuer(NoLockCouponIssuer.class), couponId, REQUEST_COUNT, i -> i);
		result.print(NoLockCouponIssuer.class, "사용자 1000명 → 100장");
		long issuedRows = issuedCouponRepository.countByCouponId(couponId);
		System.out.printf("[동시성] %-28s | 발급 행 %d, 카운터 %d (카운터가 실제 발급 수를 따라가지 못함)%n",
			"NoLockCouponIssuer", issuedRows, issuedQuantity(couponId));

		assertThat(issuedRows).as("초과 발급").isGreaterThan(TOTAL_QUANTITY);
		assertThat(issuedQuantity(couponId)).as("갱신 유실: 카운터 < 실제 발급 행 수").isLessThan((int) issuedRows);
	}

	/**
	 * 모든 스레드를 준비시킨 뒤 동시에 출발시켜 요청을 보낸다.
	 *
	 * @param userIdOf 요청 순번(1부터) → 사용자 id
	 */
	private Result runConcurrently(CouponIssuer issuer, Long couponId, int requestCount,
		LongUnaryOperator userIdOf) throws InterruptedException {
		AtomicInteger success = new AtomicInteger();
		Map<String, LongAdder> failures = new ConcurrentHashMap<>();
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(requestCount);

		ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
		long elapsedMillis;
		try {
			for (long i = 1; i <= requestCount; i++) {
				long userId = userIdOf.applyAsLong(i);
				executor.submit(() -> {
					try {
						start.await();
						issuer.issue(couponId, userId);
						success.incrementAndGet();
					} catch (CouponException e) {
						failures.computeIfAbsent(e.getErrorCode().name(), k -> new LongAdder()).increment();
					} catch (Exception e) {
						failures.computeIfAbsent(e.getClass().getSimpleName(), k -> new LongAdder()).increment();
					} finally {
						done.countDown();
					}
				});
			}
			long startedAt = System.nanoTime();
			start.countDown();
			assertThat(done.await(120, TimeUnit.SECONDS)).as("모든 요청이 120초 안에 끝나야 한다").isTrue();
			elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
		} finally {
			executor.shutdownNow();
		}
		Map<String, Long> failureCounts = new TreeMap<>();
		failures.forEach((code, count) -> failureCounts.put(code, count.sum()));
		return new Result(success.get(), failureCounts, elapsedMillis);
	}

	private record Result(int success, Map<String, Long> failures, long elapsedMillis) {

		void print(Class<?> type, String scenario) {
			System.out.printf("[동시성] %-28s | %-24s | 성공 %4d | 실패 %s | %,6d ms%n",
				type.getSimpleName(), scenario, success, failures, elapsedMillis);
		}
	}
}
