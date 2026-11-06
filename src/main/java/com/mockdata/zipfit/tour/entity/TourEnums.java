package com.mockdata.zipfit.tour.entity;

/** 실시간 투어에서 쓰는 값들 */
public final class TourEnums {

	private TourEnums() {
	}

	public enum TourStatus {
		IN_PROGRESS, ENDED
	}

	/** 종료 사유: 방문자가 종료 / 방문자가 제한 시간 안에 돌아오지 않음 / 방문자가 Workspace를 나감 / Workspace 삭제 */
	public enum EndReason {
		VISITOR_ENDED, VISITOR_TIMEOUT, VISITOR_LEFT_WORKSPACE, WORKSPACE_DELETED
	}

	/** 방문자는 영상을 송출하고, 시청자는 받는다. */
	public enum TourRole {
		VISITOR, VIEWER
	}

	/** 참여자가 보고하는 WebRTC 연결 상태 (SIG-04) */
	public enum ConnectionState {
		CONNECTING, CONNECTED, DISCONNECTED
	}
}
