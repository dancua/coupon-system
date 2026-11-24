package io.github.dancua.coupon.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.dancua.coupon.domain.IssuedCoupon;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCoupon, Long> {

	List<IssuedCoupon> findAllByUserIdOrderByIssuedAtDesc(Long userId);

	boolean existsByCouponIdAndUserId(Long couponId, Long userId);

	long countByCouponId(Long couponId);

	/** Redis 발급 사용자 Set을 DB 기준으로 다시 만들 때 쓴다. */
	@Query("select i.userId from IssuedCoupon i where i.couponId = :couponId")
	List<Long> findUserIdsByCouponId(@Param("couponId") Long couponId);
}
