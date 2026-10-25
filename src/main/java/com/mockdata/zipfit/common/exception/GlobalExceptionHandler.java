package com.mockdata.zipfit.common.exception;

import com.mockdata.zipfit.auth.dto.AccountLinkRequiredResponse;
import com.mockdata.zipfit.auth.service.AccountLinkRequiredException;
import com.mockdata.zipfit.common.exception.ErrorResponse.FieldError;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final String REQUIRED_MESSAGE = "필수 값입니다.";

	@ExceptionHandler(AccountLinkRequiredException.class)
	public ResponseEntity<AccountLinkRequiredResponse> handleAccountLinkRequired(AccountLinkRequiredException e) {
		return ResponseEntity.status(e.getErrorCode().getStatus())
			.body(AccountLinkRequiredResponse.of(e.getLinkToken(), e.getEmail()));
	}

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
		ErrorCode errorCode = e.getErrorCode();
		return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, e.getMessage()));
	}

	// COM-10: 필수값 누락과 형식 오류는 필드별로 안내한다.
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		List<FieldError> errors = e.getBindingResult().getFieldErrors().stream()
			.map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
			.toList();
		return ResponseEntity.badRequest().body(ErrorResponse.invalidInput(errors));
	}

	// @RequestParam 등 메서드 파라미터 검증 실패
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
		List<FieldError> errors = e.getParameterValidationResults().stream()
			.flatMap(result -> result.getResolvableErrors().stream()
				.map(error -> new FieldError(result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
			.toList();
		return ResponseEntity.badRequest().body(ErrorResponse.invalidInput(errors));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
		return ResponseEntity.badRequest()
			.body(ErrorResponse.invalidInput(List.of(new FieldError(e.getParameterName(), REQUIRED_MESSAGE))));
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
		return ResponseEntity.badRequest()
			.body(ErrorResponse.invalidInput(List.of(new FieldError(e.getRequestPartName(), REQUIRED_MESSAGE))));
	}

	// 본문이 JSON이 아니거나 타입이 맞지 않는 경우
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
		return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_INPUT));
	}

	// 업로드 용량 제한(spring.servlet.multipart.max-file-size) 초과. 지금은 프로필 이미지만 업로드한다.
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
		return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.IMAGE_TOO_LARGE));
	}

	// 동시 요청으로 유니크 제약(닉네임, 소셜 계정 등)에 걸린 경우
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
		log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
		return ResponseEntity.status(ErrorCode.DUPLICATE_RESOURCE.getStatus())
			.body(ErrorResponse.of(ErrorCode.DUPLICATE_RESOURCE));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		// 404·405·415처럼 Spring MVC가 상태 코드를 정해 둔 예외는 그 상태 그대로 응답한다.
		if (e instanceof org.springframework.web.ErrorResponse springError && springError.getStatusCode().is4xxClientError()) {
			ErrorCode errorCode = toErrorCode(springError.getStatusCode());
			return ResponseEntity.status(springError.getStatusCode()).body(ErrorResponse.of(errorCode));
		}
		log.error("Unhandled exception", e);
		return ResponseEntity.internalServerError().body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
	}

	private static ErrorCode toErrorCode(HttpStatusCode status) {
		if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
			return ErrorCode.NOT_FOUND;
		}
		if (status.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
			return ErrorCode.METHOD_NOT_ALLOWED;
		}
		if (status.isSameCodeAs(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
			return ErrorCode.UNSUPPORTED_MEDIA_TYPE;
		}
		return ErrorCode.INVALID_INPUT;
	}
}
