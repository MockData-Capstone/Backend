package com.mockdata.zipfit.notification.entity;

/**
 * 알림 유형 (NOTI-01). 초대는 받는 사람을 시스템이 알 수 없어 유형에서 뺐다.
 * 유형마다 눌렀을 때 이동할 화면이 정해져 있다.
 */
public enum NotificationType {
	/** 새 멤버 입장 → 멤버 목록 */
	MEMBER_JOINED,
	/** 새 메시지 → 채팅. Workspace별로 읽지 않은 1건에 묶는다. */
	NEW_MESSAGE,
	/** AI 분석 완료 → 추천 위치 */
	ANALYSIS_COMPLETED,
	/** 후보지가 추가되어 투표가 필요함 → 후보지 (제안) */
	VOTE_NEEDED,
	/** 실시간 투어 시작 → 투어 */
	TOUR_STARTED
}
