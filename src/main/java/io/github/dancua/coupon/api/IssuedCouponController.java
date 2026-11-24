package io.github.dancua.coupon.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.dancua.coupon.application.CouponService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/issued-coupons")
@RequiredArgsConstructor
public class IssuedCouponController {

	private final CouponService couponService;

	@GetMapping
	public List<IssuedCouponResponse> getMine(@RequestHeader(ApiHeaders.USER_ID) Long userId) {
		return couponService.getIssuedCoupons(userId).stream()
			.map(IssuedCouponResponse::from)
			.toList();
	}

	@PostMapping("/{issuedCouponId}/use")
	public ResponseEntity<Void> use(@PathVariable Long issuedCouponId,
		@RequestHeader(ApiHeaders.USER_ID) Long userId) {
		couponService.use(issuedCouponId, userId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{issuedCouponId}/cancel")
	public ResponseEntity<Void> cancelUse(@PathVariable Long issuedCouponId,
		@RequestHeader(ApiHeaders.USER_ID) Long userId) {
		couponService.cancelUse(issuedCouponId, userId);
		return ResponseEntity.noContent().build();
	}
}
