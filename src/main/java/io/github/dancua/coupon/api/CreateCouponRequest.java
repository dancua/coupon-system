package io.github.dancua.coupon.api;

import java.time.LocalDateTime;

import io.github.dancua.coupon.application.CreateCouponCommand;
import io.github.dancua.coupon.domain.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCouponRequest(
	@NotBlank @Size(max = 100) String name,
	@NotNull DiscountType discountType,
	// int로 두면 값이 빠졌을 때 Jackson이 역직렬화 단계에서 실패해 필드 오류를 돌려줄 수 없다
	@NotNull @Positive Integer discountValue,
	@NotNull @Positive Integer totalQuantity,
	@NotNull LocalDateTime issueStartAt,
	@NotNull LocalDateTime issueEndAt,
	@NotNull LocalDateTime expiresAt
) {

	public CreateCouponCommand toCommand() {
		return new CreateCouponCommand(name, discountType, discountValue, totalQuantity,
			issueStartAt, issueEndAt, expiresAt);
	}
}
