package io.github.dancua.coupon.api;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.dancua.coupon.application.CouponIssuer;
import io.github.dancua.coupon.application.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

	private final CouponService couponService;
	private final CouponIssuer couponIssuer;

	@PostMapping
	public ResponseEntity<IdResponse> create(@Valid @RequestBody CreateCouponRequest request) {
		Long couponId = couponService.createCoupon(request.toCommand());
		return ResponseEntity.created(URI.create("/api/coupons/" + couponId)).body(new IdResponse(couponId));
	}

	@GetMapping("/{couponId}")
	public CouponResponse get(@PathVariable Long couponId) {
		return CouponResponse.from(couponService.getCoupon(couponId));
	}

	@PostMapping("/{couponId}/issue")
	public ResponseEntity<IdResponse> issue(@PathVariable Long couponId,
		@RequestHeader(ApiHeaders.USER_ID) Long userId) {
		Long issuedCouponId = couponIssuer.issue(couponId, userId);
		return ResponseEntity.created(URI.create("/api/issued-coupons/" + issuedCouponId))
			.body(new IdResponse(issuedCouponId));
	}
}
