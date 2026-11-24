package io.github.dancua.coupon.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import io.github.dancua.coupon.application.CouponService;
import io.github.dancua.coupon.application.CreateCouponCommand;
import io.github.dancua.coupon.application.NoLockCouponIssuer;
import io.github.dancua.coupon.domain.CouponErrorCode;
import io.github.dancua.coupon.domain.CouponException;
import io.github.dancua.coupon.domain.DiscountType;
import io.github.dancua.coupon.infrastructure.CouponJpaRepository;
import io.github.dancua.coupon.infrastructure.IssuedCouponJpaRepository;

/**
 * 모든 @SpringBootTest 통합 테스트의 공통 부모.
 * <p>
 * Spring은 설정(어노테이션, @Import 목록)이 똑같은 테스트끼리 컨텍스트를 재사용한다. 설정을 이 클래스 한 곳에만 두면
 * 모든 통합 테스트가 하나의 컨텍스트, 즉 MySQL / Redis 컨테이너 한 쌍을 함께 쓴다.
 * 그래서 하위 클래스에는 @Import, @TestConfiguration, @MockitoBean 같은 컨텍스트 설정을 추가하지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, IntegrationTestSupport.FixedClockConfig.class, NoLockCouponIssuer.class})
public abstract class IntegrationTestSupport {

	protected static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	/** 테스트 시계의 현재 시각: 2026-12-01 11:00 */
	protected static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 1, 11, 0);

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfig {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
		}
	}

	@Autowired
	protected ApplicationContext context;

	@Autowired
	protected CouponService couponService;

	@Autowired
	protected CouponJpaRepository couponRepository;

	@Autowired
	protected IssuedCouponJpaRepository issuedCouponRepository;

	@Autowired
	protected StringRedisTemplate redisTemplate;

	@AfterEach
	void cleanUpStores() {
		issuedCouponRepository.deleteAllInBatch();
		couponRepository.deleteAllInBatch();
		redisTemplate.delete(redisTemplate.keys("coupon:*"));
	}

	/** 지금(NOW) 발급 중인 정액 쿠폰을 만든다. 발급 기간: NOW ± 1시간, 사용 만료: NOW + 30일 */
	protected Long createCoupon(int totalQuantity) {
		return couponService.createCoupon(new CreateCouponCommand(
			"선착순 3000원 할인", DiscountType.FIXED, 3000, totalQuantity,
			NOW.minusHours(1), NOW.plusHours(1), NOW.plusDays(30)));
	}

	protected int issuedQuantity(Long couponId) {
		return couponRepository.findById(couponId).orElseThrow().getIssuedQuantity();
	}

	protected static void assertCouponError(Runnable action, CouponErrorCode expected) {
		assertThatThrownBy(action::run)
			.isInstanceOf(CouponException.class)
			.extracting("errorCode")
			.isEqualTo(expected);
	}
}
