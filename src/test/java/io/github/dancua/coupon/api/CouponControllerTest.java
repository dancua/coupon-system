package io.github.dancua.coupon.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.dancua.coupon.application.CouponIssuer;
import io.github.dancua.coupon.application.CouponService;
import io.github.dancua.coupon.domain.CouponErrorCode;
import io.github.dancua.coupon.domain.CouponException;

@WebMvcTest({CouponController.class, IssuedCouponController.class})
class CouponControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CouponService couponService;

	@MockitoBean
	private CouponIssuer couponIssuer;

	@Test
	@DisplayName("쿠폰 생성 성공 시 201과 Location 헤더를 돌려준다")
	void create() throws Exception {
		given(couponService.createCoupon(any())).willReturn(1L);

		mockMvc.perform(post("/api/coupons")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "선착순 3000원 할인",
					  "discountType": "FIXED",
					  "discountValue": 3000,
					  "totalQuantity": 100,
					  "issueStartAt": "2026-12-01T10:00:00",
					  "issueEndAt": "2026-12-01T12:00:00",
					  "expiresAt": "2026-12-31T23:59:00"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/api/coupons/1"))
			.andExpect(jsonPath("$.id").value(1));
	}

	@Test
	@DisplayName("필수 값이 빠진 쿠폰 생성 요청은 400과 필드 오류를 돌려준다")
	void createInvalid() throws Exception {
		mockMvc.perform(post("/api/coupons")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "name": "", "discountValue": 0, "totalQuantity": 10 }
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.fieldErrors.length()").value(6));
	}

	@Test
	@DisplayName("숫자 필드가 아예 빠져도 필드 오류로 알려 준다")
	void createMissingNumbers() throws Exception {
		mockMvc.perform(post("/api/coupons")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "name": "" }
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.length()").value(7));
	}

	@Test
	@DisplayName("발급 성공 시 201과 발급 쿠폰 id를 돌려준다")
	void issue() throws Exception {
		given(couponIssuer.issue(1L, 7L)).willReturn(10L);

		mockMvc.perform(post("/api/coupons/1/issue").header(ApiHeaders.USER_ID, 7))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/api/issued-coupons/10"))
			.andExpect(jsonPath("$.id").value(10));
	}

	@Test
	@DisplayName("수량이 소진되면 409와 에러 코드를 돌려준다")
	void issueExhausted() throws Exception {
		given(couponIssuer.issue(1L, 7L))
			.willThrow(new CouponException(CouponErrorCode.COUPON_QUANTITY_EXHAUSTED));

		mockMvc.perform(post("/api/coupons/1/issue").header(ApiHeaders.USER_ID, 7))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("COUPON_QUANTITY_EXHAUSTED"));
	}

	@Test
	@DisplayName("X-User-Id 헤더가 없으면 400을 돌려준다")
	void missingUserHeader() throws Exception {
		mockMvc.perform(post("/api/coupons/1/issue"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("없는 쿠폰을 조회하면 404를 돌려준다")
	void getNotFound() throws Exception {
		given(couponService.getCoupon(999L)).willThrow(new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

		mockMvc.perform(get("/api/coupons/999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("COUPON_NOT_FOUND"));
	}

	@Test
	@DisplayName("내 쿠폰 목록과 사용 API")
	void myCouponsAndUse() throws Exception {
		given(couponService.getIssuedCoupons(7L)).willReturn(List.of());

		mockMvc.perform(get("/api/issued-coupons").header(ApiHeaders.USER_ID, 7))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(post("/api/issued-coupons/10/use").header(ApiHeaders.USER_ID, 7))
			.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/issued-coupons/10/cancel").header(ApiHeaders.USER_ID, 7))
			.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("POST만 있는 주소에 GET으로 요청하면 405를 돌려준다")
	void methodNotAllowed() throws Exception {
		mockMvc.perform(get("/api/coupons") )
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	@DisplayName("없는 주소로 요청하면 404를 돌려준다.")
	void getNotPage() throws Exception {
		mockMvc.perform(get("/api/nothing"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}
}
