package io.github.dancua.coupon.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import io.github.dancua.coupon.domain.CouponErrorCode;
import io.github.dancua.coupon.domain.CouponException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final String INVALID_REQUEST = "INVALID_REQUEST";

	@ExceptionHandler(CouponException.class)
	public ResponseEntity<ErrorResponse> handleCouponException(CouponException e) {
		CouponErrorCode code = e.getErrorCode();
		return ResponseEntity.status(statusOf(code)).body(ErrorResponse.of(code.name(), e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		var fieldErrors = e.getBindingResult().getFieldErrors().stream()
			.map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
			.toList();
		return ResponseEntity.badRequest()
			.body(new ErrorResponse(INVALID_REQUEST, "요청 값이 올바르지 않습니다.", fieldErrors));
	}

	@ExceptionHandler({
		MissingRequestHeaderException.class,
		MethodArgumentTypeMismatchException.class,
		HttpMessageNotReadableException.class
	})
	public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
		return ResponseEntity.badRequest().body(ErrorResponse.of(INVALID_REQUEST, "요청 형식이 올바르지 않습니다."));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("처리되지 않은 예외", e);
		return ResponseEntity.internalServerError()
			.body(ErrorResponse.of("INTERNAL_ERROR", "일시적인 오류가 발생했습니다."));
	}

	/** enum의 모든 값을 switch로 다루므로, 에러 코드를 추가하면 여기서 컴파일 에러가 나 매핑을 빠뜨리지 않는다. */
	private static HttpStatus statusOf(CouponErrorCode code) {
		return switch (code) {
			case COUPON_POLICY_INVALID -> HttpStatus.BAD_REQUEST;
			case COUPON_NOT_FOUND, ISSUED_COUPON_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case COUPON_ALREADY_ISSUED, COUPON_QUANTITY_EXHAUSTED, COUPON_NOT_IN_ISSUE_PERIOD,
				COUPON_ALREADY_USED, COUPON_EXPIRED, COUPON_NOT_USED -> HttpStatus.CONFLICT;
			case COUPON_ISSUE_BUSY -> HttpStatus.SERVICE_UNAVAILABLE;
		};
	}
}
