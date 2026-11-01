package com.mockdata.zipfit.ai;

/** AI 서버 호출 실패. 호출한 기능만 실패로 처리하고 다른 기능에는 영향을 주지 않는다 (COM-04). */
public class AiServerException extends RuntimeException {

	public AiServerException(String message, Throwable cause) {
		super(message, cause);
	}

	public AiServerException(String message) {
		super(message);
	}
}
