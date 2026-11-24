package io.github.dancua.coupon.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자에게 발급된 쿠폰.
 * <p>
 * Coupon과 연관관계 대신 couponId만 들고 있다. 발급 경로(DB 락, Redis, Kafka Consumer)가 바뀌어도
 * Coupon 엔티티를 함께 로딩할 필요가 없게 하기 위해서다. 만료 시각은 발급 시점에 복사해 둔다.
 * <p>
 * (coupon_id, user_id) unique 제약은 애플리케이션의 중복 방지가 뚫렸을 때의 마지막 방어선이다.
 */
@Entity
@Table(
	name = "issued_coupon",
	uniqueConstraints = @UniqueConstraint(name = "uk_issued_coupon_coupon_user", columnNames = {"coupon_id", "user_id"}),
	indexes = @Index(name = "idx_issued_coupon_user", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssuedCoupon {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "coupon_id", nullable = false)
	private Long couponId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private IssuedCouponStatus status;

	@Column(nullable = false)
	private LocalDateTime issuedAt;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	private LocalDateTime usedAt;

	private IssuedCoupon(Long couponId, Long userId, LocalDateTime issuedAt, LocalDateTime expiresAt) {
		this.couponId = couponId;
		this.userId = userId;
		this.status = IssuedCouponStatus.ISSUED;
		this.issuedAt = issuedAt;
		this.expiresAt = expiresAt;
	}

	public static IssuedCoupon issue(Long couponId, Long userId, LocalDateTime issuedAt, LocalDateTime expiresAt) {
		if (couponId == null || userId == null || issuedAt == null || expiresAt == null) {
			throw new IllegalArgumentException("couponId, userId, issuedAt, expiresAt은 필수입니다");
		}
		return new IssuedCoupon(couponId, userId, issuedAt, expiresAt);
	}

	public void use(LocalDateTime now) {
		if (status == IssuedCouponStatus.USED) {
			throw new CouponException(CouponErrorCode.COUPON_ALREADY_USED);
		}
		if (status == IssuedCouponStatus.EXPIRED || isExpiredAt(now)) {
			throw new CouponException(CouponErrorCode.COUPON_EXPIRED);
		}
		this.status = IssuedCouponStatus.USED;
		this.usedAt = now;
	}

	public void cancelUse() {
		if (status != IssuedCouponStatus.USED) {
			throw new CouponException(CouponErrorCode.COUPON_NOT_USED);
		}
		this.status = IssuedCouponStatus.ISSUED;
		this.usedAt = null;
	}

	/**
	 * 만료 시각이 지난 미사용 쿠폰을 EXPIRED로 바꾼다. 조건에 맞지 않으면 아무것도 하지 않는다(스케줄러용).
	 */
	public void expire(LocalDateTime now) {
		if (status == IssuedCouponStatus.ISSUED && isExpiredAt(now)) {
			this.status = IssuedCouponStatus.EXPIRED;
		}
	}

	private boolean isExpiredAt(LocalDateTime now) {
		return !now.isBefore(expiresAt);
	}
}
