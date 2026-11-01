package com.mockdata.zipfit.common.event;

/**
 * 회원을 지우기 직전에 같은 트랜잭션 안에서 동기로 발행한다 (AUTH-06).
 * 받는 쪽은 탈퇴를 막아야 하면 예외를 던지고, 아니면 그 회원의 데이터를 정리한다.
 */
public record UserWithdrawingEvent(Long userId) {
}
