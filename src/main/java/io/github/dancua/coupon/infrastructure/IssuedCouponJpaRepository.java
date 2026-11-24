package io.github.dancua.coupon.infrastructure;

import io.github.dancua.coupon.domain.IssuedCoupon;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCoupon, Long> {

    List<IssuedCoupon> findAllByUserIdOrderByIssuedAtDesc(Long userId);

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);

    long countByCouponId(Long couponId);
}
