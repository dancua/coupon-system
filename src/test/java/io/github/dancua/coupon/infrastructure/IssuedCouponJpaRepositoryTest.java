package io.github.dancua.coupon.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dancua.coupon.domain.Coupon;
import io.github.dancua.coupon.domain.DiscountType;
import io.github.dancua.coupon.domain.IssuedCoupon;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class IssuedCouponJpaRepositoryTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 12, 1, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 12, 1, 12, 0);
    private static final LocalDateTime EXPIRED_AT = LocalDateTime.of(2026, 12, 31, 23, 59);

    @Autowired
    private CouponJpaRepository couponRepository;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponRepository;

    private Coupon coupon;

    @BeforeEach
    void setUp() {
        coupon = couponRepository.save(
                Coupon.create("선착순 쿠폰", DiscountType.FIXED, 1000, 100, START, END, EXPIRED_AT));
    }

    @Test
    @DisplayName("쿠폰 정책을 저장하고 조회할 수 있다")
    void saveCoupon() {
        Coupon found = couponRepository.findById(coupon.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("선착순 쿠폰");
        assertThat(found.getTotalQuantity()).isEqualTo(100);
        assertThat(found.getIssuedQuantity()).isZero();
    }

    @Test
    @DisplayName("같은 사용자에게 같은 쿠폰을 두 번 저장하면 unique 제약 위반이 발생한다")
    void duplicateIssue() {
        issuedCouponRepository.saveAndFlush(IssuedCoupon.issue(coupon.getId(), 7L, START, EXPIRED_AT));

        assertThatThrownBy(() -> issuedCouponRepository.saveAndFlush(
                IssuedCoupon.issue(coupon.getId(), 7L, START, EXPIRED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("다른 사용자는 같은 쿠폰을 각각 발급받을 수 있다")
    void differentUsers() {
        issuedCouponRepository.saveAndFlush(IssuedCoupon.issue(coupon.getId(), 7L, START, EXPIRED_AT));
        issuedCouponRepository.saveAndFlush(IssuedCoupon.issue(coupon.getId(), 8L, START, EXPIRED_AT));

        assertThat(issuedCouponRepository.countByCouponId(coupon.getId())).isEqualTo(2);
        assertThat(issuedCouponRepository.existsByCouponIdAndUserId(coupon.getId(), 7L)).isTrue();
        assertThat(issuedCouponRepository.existsByCouponIdAndUserId(coupon.getId(), 9L)).isFalse();
    }

    @Test
    @DisplayName("사용자의 발급 쿠폰을 최신 발급 순으로 조회한다")
    void findByUser() {
        Coupon other = couponRepository.save(
                Coupon.create("두 번째 쿠폰", DiscountType.RATE, 10, 50, START, END, EXPIRED_AT));
        issuedCouponRepository.save(IssuedCoupon.issue(coupon.getId(), 7L, START, EXPIRED_AT));
        issuedCouponRepository.save(IssuedCoupon.issue(other.getId(), 7L, START.plusMinutes(5), EXPIRED_AT));
        issuedCouponRepository.save(IssuedCoupon.issue(coupon.getId(), 8L, START, EXPIRED_AT));

        List<IssuedCoupon> result = issuedCouponRepository.findAllByUserIdOrderByIssuedAtDesc(7L);

        assertThat(result).extracting(IssuedCoupon::getCouponId)
                .containsExactly(other.getId(), coupon.getId());
    }
}
