package io.github.dancua.coupon.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import io.github.dancua.coupon.domain.DiscountType;
import io.github.dancua.coupon.infrastructure.CouponJpaRepository;
import io.github.dancua.coupon.infrastructure.IssuedCouponJpaRepository;

/**
 * 동시 발급 요청에서 총 수량이 지켜지는지 검증한다.
 * <p>
 * 동시성 제어가 없는 현재 구현에서는 이 테스트가 실패한다(초과 발급 재현). 실행 방법:
 * {@code ./gradlew concurrencyTest}
 * Phase 4에서 락 / Redis로 해결한 뒤 기본 test 태스크에 포함시킨다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("concurrency")
class CouponIssueConcurrencyTest {

	private static final int TOTAL_QUANTITY = 100;
	private static final int REQUEST_COUNT = 1000;
	private static final int THREAD_POOL_SIZE = 32;

	@Autowired
	private CouponService couponService;

	@Autowired
	private CouponJpaRepository couponRepository;

	@Autowired
	private IssuedCouponJpaRepository issuedCouponRepository;

	@AfterEach
	void tearDown() {
		issuedCouponRepository.deleteAllInBatch();
		couponRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("서로 다른 사용자 1000명이 동시에 요청해도 정확히 100장만 발급된다")
	void issueConcurrently() throws InterruptedException {
		LocalDateTime now = LocalDateTime.now();
		Long couponId = couponService.createCoupon(new CreateCouponCommand(
			"동시성 테스트 쿠폰", DiscountType.FIXED, 1000, TOTAL_QUANTITY,
			now.minusHours(1), now.plusHours(1), now.plusDays(1)));

		AtomicInteger success = new AtomicInteger();
		AtomicInteger failure = new AtomicInteger();
		CountDownLatch ready = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(REQUEST_COUNT);

		ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
		try {
			for (long userId = 1; userId <= REQUEST_COUNT; userId++) {
				long user = userId;
				executor.submit(() -> {
					try {
						ready.await();
						couponService.issue(couponId, user);
						success.incrementAndGet();
					} catch (Exception e) {
						failure.incrementAndGet();
					} finally {
						done.countDown();
					}
				});
			}
			ready.countDown();
			assertThat(done.await(60, TimeUnit.SECONDS)).as("모든 요청이 60초 안에 끝나야 한다").isTrue();
		} finally {
			executor.shutdownNow();
		}

		long issuedRows = issuedCouponRepository.countByCouponId(couponId);
		int issuedQuantity = couponRepository.findById(couponId).orElseThrow().getIssuedQuantity();
		System.out.printf("[동시성] 성공=%d, 실패=%d, 발급 행 수=%d, coupon.issuedQuantity=%d (총 수량 %d)%n",
			success.get(), failure.get(), issuedRows, issuedQuantity, TOTAL_QUANTITY);

		assertThat(issuedRows).as("실제 발급된 쿠폰 수").isEqualTo(TOTAL_QUANTITY);
		assertThat(issuedQuantity).as("쿠폰의 발급 수량 카운터").isEqualTo(TOTAL_QUANTITY);
	}
}
