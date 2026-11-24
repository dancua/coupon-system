package io.github.dancua.coupon.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.dancua.coupon.domain.Coupon;
import jakarta.persistence.LockModeType;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {

	/** 비관적 락: SELECT ... FOR UPDATE. 트랜잭션이 끝날 때까지 다른 트랜잭션의 같은 행 락 요청을 대기시킨다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Coupon c where c.id = :id")
	Optional<Coupon> findByIdForUpdate(@Param("id") Long id);

	/**
	 * 남은 수량이 있을 때만 발급 수량을 1 늘린다. 조건 확인과 증가가 UPDATE 한 문장 안에서 일어나므로 원자적이다.
	 * Redis 발급 경로에서 DB 카운터를 맞추고, Redis 데이터가 유실됐을 때 초과 발급을 막는 안전장치로 쓴다.
	 * <p>
	 * JPQL 일괄 UPDATE는 기본적으로 @Version을 올리지 않는다. {@code versioned}를 붙이지 않으면 이 UPDATE 사이에
	 * 끼어든 낙관적 락 경로가 버전 충돌을 감지하지 못하고 오래된 issuedQuantity로 덮어써 갱신이 유실된다.
	 *
	 * @return 갱신된 행 수 (0이면 이미 소진)
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update versioned Coupon c set c.issuedQuantity = c.issuedQuantity + 1 "
		+ "where c.id = :id and c.issuedQuantity < c.totalQuantity")
	int increaseIssuedQuantity(@Param("id") Long id);
}
