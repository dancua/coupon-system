package io.github.dancua.coupon.infrastructure;

import io.github.dancua.coupon.domain.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {
}
