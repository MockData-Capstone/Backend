package com.mockdata.zipfit.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
		ErrorCode errorCode = e.getErrorCode();
		return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
			.findFirst()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.orElse(ErrorCode.INVALID_INPUT.getMessage());
		return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_INPUT, message));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
		return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_INPUT));
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
		log.error("Unhandled exception", e);
		return ResponseEntity.internalServerError().body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
	}
}
