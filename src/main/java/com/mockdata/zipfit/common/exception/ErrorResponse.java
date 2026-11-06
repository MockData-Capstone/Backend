package com.mockdata.zipfit.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * @param errors 입력 검증 실패 시 필드별 안내 (COM-10). 그 밖의 에러에서는 응답에 넣지 않는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, List<FieldError> errors) {

	public record FieldError(String field, String message) {
	}

	public static ErrorResponse of(ErrorCode errorCode) {
		return of(errorCode, errorCode.getMessage());
	}

	public static ErrorResponse of(ErrorCode errorCode, String message) {
		return new ErrorResponse(errorCode.name(), message, null);
	}

	/** message에는 첫 번째 필드의 안내를 넣는다. */
	public static ErrorResponse invalidInput(List<FieldError> errors) {
		String message = errors.isEmpty()
			? ErrorCode.INVALID_INPUT.getMessage()
			: errors.getFirst().field() + ": " + errors.getFirst().message();
		return new ErrorResponse(ErrorCode.INVALID_INPUT.name(), message, errors);
	}
}
