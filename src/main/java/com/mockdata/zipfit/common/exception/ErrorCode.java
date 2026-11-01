package com.mockdata.zipfit.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
	// 공통
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "접근 토큰이 만료되었습니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
	DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "이미 존재하는 데이터입니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

	// AUTH
	UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 로그인 방식입니다."),
	OAUTH_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다. 다시 시도해주세요."),
	INVALID_SIGNUP_TOKEN(HttpStatus.UNAUTHORIZED, "가입 정보가 만료되었습니다. 다시 로그인해주세요."),
	PRIVACY_AGREEMENT_REQUIRED(HttpStatus.BAD_REQUEST, "개인정보 수집·이용에 동의해야 가입할 수 있습니다."),
	ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 계정입니다. 다시 로그인해주세요."),
	DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
	DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해주세요."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
	INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 올바르지 않습니다."),
	PASSWORD_NOT_SET(HttpStatus.BAD_REQUEST, "비밀번호 없이 소셜 로그인으로 가입한 회원입니다. 소셜 로그인을 이용해주세요."),
	INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "재설정 링크가 만료되었거나 이미 사용되었습니다. 다시 요청해주세요."),

	// AUTH-08 계정 연동
	ACCOUNT_LINK_REQUIRED(HttpStatus.CONFLICT, "같은 이메일로 가입된 계정이 있습니다. 비밀번호를 확인해 계정을 연동해주세요."),
	INVALID_LINK_TOKEN(HttpStatus.UNAUTHORIZED, "연동 정보가 만료되었습니다. 다시 로그인해주세요."),
	SOCIAL_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 다른 회원에 연동된 소셜 계정입니다."),
	PROVIDER_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 연동된 로그인 방식입니다."),

	// AUTH-05 프로필 이미지
	INVALID_IMAGE(HttpStatus.BAD_REQUEST, "jpg, png, webp 이미지만 올릴 수 있습니다."),
	IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지는 5MB 이하만 올릴 수 있습니다."),

	// AUTH-06 회원 탈퇴
	HOST_DELEGATION_REQUIRED(HttpStatus.CONFLICT, "다른 멤버가 있는 Workspace의 호스트는 호스트를 위임한 뒤 탈퇴할 수 있습니다."),

	// WS Workspace
	WORKSPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "Workspace를 찾을 수 없습니다."),
	NOT_WORKSPACE_MEMBER(HttpStatus.FORBIDDEN, "Workspace 멤버만 이용할 수 있습니다."),
	NOT_WORKSPACE_HOST(HttpStatus.FORBIDDEN, "호스트만 할 수 있습니다."),

	// MEM 멤버·초대
	INVITATION_EXPIRED(HttpStatus.GONE, "만료된 초대 링크입니다."),
	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "Workspace 멤버가 아닙니다."),
	CANNOT_DELEGATE_TO_SELF(HttpStatus.BAD_REQUEST, "본인에게는 호스트를 위임할 수 없습니다."),
	CANNOT_KICK_HOST(HttpStatus.BAD_REQUEST, "호스트 본인은 내보낼 수 없습니다."),
	HOST_CANNOT_LEAVE(HttpStatus.CONFLICT, "호스트는 다른 멤버에게 호스트를 위임한 뒤 나갈 수 있습니다."),
	LAST_MEMBER_CANNOT_LEAVE(HttpStatus.CONFLICT, "혼자 남은 Workspace는 나갈 수 없습니다. Workspace를 삭제해주세요."),

	// CHAT 채팅
	EMPTY_MESSAGE(HttpStatus.BAD_REQUEST, "빈 메시지는 보낼 수 없습니다."),
	MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, "메시지는 1,000자까지 보낼 수 있습니다."),

	// REC AI 분석·추천
	NO_CONVERSATION(HttpStatus.CONFLICT, "대화 기록이 없어 분석할 수 없습니다. 먼저 채팅으로 원하는 집 조건을 이야기해주세요."),
	ANALYSIS_IN_PROGRESS(HttpStatus.CONFLICT, "이미 분석 중입니다. 분석이 끝나면 알려드릴게요."),

	// CAND 후보지
	RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "추천 위치를 찾을 수 없습니다."),
	NO_COORDINATES(HttpStatus.BAD_REQUEST, "좌표가 없는 위치는 후보지로 추가할 수 없습니다."),
	DUPLICATE_CANDIDATE(HttpStatus.CONFLICT, "이미 후보지로 추가된 위치입니다."),
	CANDIDATE_NOT_FOUND(HttpStatus.NOT_FOUND, "이미 제외되었거나 없는 후보지입니다."),
	NOT_CANDIDATE_OWNER(HttpStatus.FORBIDDEN, "후보지를 추가한 멤버나 호스트만 제외할 수 있습니다."),

	// VOTE 투표
	ALREADY_VOTED(HttpStatus.CONFLICT, "이미 투표한 후보지입니다. 투표 변경을 이용해주세요."),
	VOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "아직 투표하지 않은 후보지입니다."),

	// CMP 비교표
	NOT_ENOUGH_CANDIDATES(HttpStatus.CONFLICT, "후보지를 2개 이상 추가해주세요."),

	// GAME 미니게임
	GAME_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 미니게임이 있습니다."),
	NOT_ENOUGH_PLAYERS(HttpStatus.BAD_REQUEST, "참여 멤버를 2명 이상 골라주세요."),
	PLAYER_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "지금 접속 중인 멤버만 참여할 수 있습니다."),
	VISITOR_RULE_REQUIRED(HttpStatus.BAD_REQUEST, "가위바위보는 방문자 기준(승자/패자)을 골라주세요."),
	GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "미니게임을 찾을 수 없습니다."),
	GAME_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "이미 끝났거나 중단된 게임입니다."),
	NOT_GAME_STARTER(HttpStatus.FORBIDDEN, "게임을 시작한 멤버만 중단할 수 있습니다."),
	NOT_GAME_PLAYER(HttpStatus.FORBIDDEN, "이 게임에 남아 있는 참가자가 아닙니다."),
	WRONG_GAME_TYPE(HttpStatus.BAD_REQUEST, "이 게임 종류에서는 할 수 없는 요청입니다."),
	ROUND_CLOSED(HttpStatus.CONFLICT, "이미 끝났거나 아직 열리지 않은 라운드입니다."),
	ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이번 라운드에 이미 냈습니다."),

	// TOUR 실시간 투어
	NOT_SELECTED_VISITOR(HttpStatus.FORBIDDEN, "가장 최근 미니게임으로 선정된 방문자만 투어를 시작할 수 있습니다."),
	TOUR_ALREADY_HELD(HttpStatus.CONFLICT, "이 미니게임 결과로는 이미 투어를 했습니다. 미니게임으로 방문자를 다시 정해주세요."),
	TOUR_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 투어가 있습니다."),
	TOUR_NOT_FOUND(HttpStatus.NOT_FOUND, "투어를 찾을 수 없습니다."),
	TOUR_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "이미 종료된 투어입니다."),
	TOUR_FULL(HttpStatus.CONFLICT, "투어 인원이 가득 찼습니다."),
	NOT_TOUR_PARTICIPANT(HttpStatus.FORBIDDEN, "투어 참여자가 아닙니다."),
	ONLY_VISITOR_ALLOWED(HttpStatus.FORBIDDEN, "방문자만 할 수 있습니다."),
	VISITOR_CANNOT_LEAVE(HttpStatus.CONFLICT, "방문자는 나가기 대신 투어 종료를 이용해주세요."),

	// NOTI 알림
	NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
	PUSH_SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 푸시 구독이 없습니다."),
	INVALID_CURSOR(HttpStatus.BAD_REQUEST, "잘못된 페이지 위치입니다."),

	// COND 확정 조건
	CONDITION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 확정한 조건이 있습니다. 조건 수정을 이용해주세요."),
	CONDITION_NOT_FOUND(HttpStatus.NOT_FOUND, "아직 확정한 조건이 없습니다. 조건을 먼저 확정해주세요."),
	INVALID_RANGE(HttpStatus.BAD_REQUEST, "최솟값이 최댓값보다 클 수 없습니다."),

	// LINK 매물 검색 링크
	CONDITION_REQUIRED(HttpStatus.CONFLICT, "매물 검색 링크를 만들려면 조건을 먼저 확정해주세요."),

	// WebSocket 공통
	INVALID_SOCKET_MESSAGE(HttpStatus.BAD_REQUEST, "메시지 형식이 올바르지 않습니다."),
	UNSUPPORTED_SOCKET_MESSAGE(HttpStatus.BAD_REQUEST, "지원하지 않는 메시지입니다."),

	// SIG 시그널링
	NOT_JOINED(HttpStatus.BAD_REQUEST, "JOIN을 먼저 보내주세요."),
	PEER_NOT_FOUND(HttpStatus.BAD_REQUEST, "상대가 투어 방에 없습니다.");

	private final HttpStatus status;
	private final String message;
}
