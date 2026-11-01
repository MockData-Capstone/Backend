package com.mockdata.zipfit.signaling;

import com.mockdata.zipfit.common.websocket.WsCloseCodes;
import com.mockdata.zipfit.common.websocket.WsMessage;
import com.mockdata.zipfit.tour.service.TourEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋된 투어 변경을 시그널링 방에 반영한다.
 * 투어가 끝나면 모든 참여자에게 TOUR_ENDED를 보낸 뒤 연결을 닫고(4404, SIG-05),
 * 나가거나 내보내진 참여자는 연결을 닫는다(4403). 닫힐 때 나머지에게 PEER_LEFT가 간다.
 */
@Component
@RequiredArgsConstructor
public class SignalingEventRelay {

	private final SignalingRoomRegistry rooms;

	@TransactionalEventListener(fallbackExecution = true)
	public void relay(TourEvent event) {
		switch (event.type()) {
			case TourEvent.TOUR_ENDED -> {
				rooms.broadcast(event.tourId(), WsMessage.of(TourEvent.TOUR_ENDED, event.data()));
				rooms.closeAll(event.tourId(), WsCloseCodes.NOT_FOUND);
			}
			case TourEvent.PARTICIPANT_REMOVED -> rooms.close(event.tourId(), (Long) event.data(), WsCloseCodes.FORBIDDEN);
			default -> {
			}
		}
	}
}
