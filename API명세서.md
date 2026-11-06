# 📊 ZIPFIT API명세서

> ℹ️ Notion ZIPFIT API명세서의 로컬 미러입니다(2026-11-01 내보내기 기준). 노션 게스트 권한만 있는 팀원과 Claude 연동을 위해 저장소에 둔 사본입니다.
> **이 문서와 [`기능명세서.md`](./기능명세서.md)·[`PRD.md`](./PRD.md)가 다르면 기능명세서·PRD를 따릅니다.** 실제 구현과 차이는 [`README.md`](./README.md)의 API 개요와 맨 아래 "기능명세서와 다른 점"에 정리합니다.

## auth

| 기능명 | Method | Endpoint | 설명 |
|---|---|---|---|
| 회원가입 | POST | `/api/auth/signup` | [AUTH-01] 이메일·비밀번호·닉네임으로 계정을 생성한다. 이미 가입된 이메일이면 409를 반환하고, 비밀번호는 복호화할 수 없는 형태로 저장한다. |
| 이메일 중복 확인 | GET | `/api/auth/email-availability?email=` | [AUTH-01 보조] 가입 전 이메일 사용 가능 여부를 반환한다. 입력 중에 중복을 안내하기 위한 API다. |
| 카카오 인증 URL 조회 | GET | `/api/auth/kakao/authorize-url` | [AUTH-02 보조] 카카오 로그인 화면으로 보낼 인증 URL을 반환한다. 클라이언트에 앱 키를 노출하지 않기 위해 서버가 생성한다. |
| 카카오 로그인·가입 | POST | `/api/auth/kakao` | [AUTH-02] 인증 코드로 카카오 사용자 정보를 받아 처리한다. 이미 연동된 계정이면 로그인, 없는 회원이면 자동 생성 후 로그인한다. 같은 이메일의 자체 계정이 있으면 409와 연동용 단기 토큰을 반환하고, 이메일 제공에 동의하지 않았으면 403을 반환한다. |
| 카카오 계정 연동 확인 | POST | `/api/auth/kakao/link` | [AUTH-02] 연동용 토큰과 기존 계정의 비밀번호를 검증해 카카오 계정을 연동하고 로그인 처리한다. 비밀번호가 틀리면 연동도 로그인도 하지 않는다. |
| 로그인 | POST | `/api/auth/login` | [AUTH-03] 이메일·비밀번호를 검증해 access token과 refresh token을 발급한다. 실패 시 어느 쪽이 틀렸는지 구분하지 않고 동일한 메시지를 반환한다. |
| 로그아웃 | POST | `/api/auth/logout` | [AUTH-03] 전달받은 refresh token을 폐기해 더 이상 재발급에 쓸 수 없게 한다. |
| 토큰 재발급 | POST | `/api/auth/token/refresh` | [AUTH-03 보조] 유효한 refresh token으로 새 access token을 발급한다. 폐기·만료된 토큰이면 401을 반환한다. |
| 비밀번호 재설정 요청 | POST | `/api/auth/password-reset/request` | [AUTH-07] 가입된 이메일로 재설정 링크를 발송한다. 가입되지 않은 이메일이어도 동일한 응답을 반환한다. 카카오 전용 회원은 비밀번호가 없어 대상이 아니다. |
| 비밀번호 재설정 확인 | POST | `/api/auth/password-reset/confirm` | [AUTH-07] 재설정 토큰과 새 비밀번호를 받아 비밀번호를 교체하고 기존 refresh token을 모두 폐기한다. 토큰은 일회용이며 만료되면 재요청해야 한다. |

## user

| 기능명 | Method | Endpoint | 설명 |
|---|---|---|---|
| 내 정보 조회 | GET | `/api/users/me` | [AUTH-04] 이메일, 닉네임, 연동된 로그인 방식(자체/카카오), 비밀번호 보유 여부를 반환한다. 비밀번호 보유 여부는 비밀번호 변경 입력란 표시 여부를 결정한다. |
| 내 정보 수정 | PATCH | `/api/users/me` | [AUTH-05] 닉네임과 비밀번호를 수정한다. 전달된 필드만 변경하며 닉네임 변경은 참여 중인 모든 Workspace에 반영된다. 카카오 전용 회원은 비밀번호를 변경할 수 없다. |
| 프로필 이미지 변경 | PATCH | `/api/users/me/profile-image` | [AUTH-05 확장] 프로필 이미지를 업로드해 교체하고, 빈 값을 보내면 기본 이미지로 되돌린다. 허용 형식과 용량 제한을 초과하면 400을 반환한다. |
| 닉네임 중복 확인 | GET | `/api/users/nickname-availability?nickname=` | [AUTH-01·05 보조] 닉네임 사용 가능 여부를 반환한다. 가입과 정보 수정 두 화면에서 모두 사용한다. |
| 회원 탈퇴 | DELETE | `/api/users/me` | [AUTH-06] 계정을 탈퇴 처리하고 모든 Workspace 멤버십, 토큰, 알림, 푸시 구독을 정리한다. 작성한 채팅은 '탈퇴한 사용자'로 유지한다. 다른 멤버가 있는 Workspace의 호스트는 위임 후에만 탈퇴할 수 있다. |

## workspace

| 기능명 | Method | Endpoint | 설명 |
|---|---|---|---|
| Workspace 생성 | POST | `/api/workspaces` | [WS-01] 이름(필수)과 설명(선택)으로 Workspace를 만들고 생성자를 호스트로 등록한다. 이름은 30자, 설명은 200자까지 받는다. |
| 내 Workspace 목록 조회 | GET | `/api/workspaces` | [WS-02] 내가 속한 Workspace 목록을 반환한다. 각 항목에 이름, 멤버 수, 내 역할, 최근 활동 시각을 담고 최근 활동순으로 정렬한다. |
| Workspace 상세 조회 | GET | `/api/workspaces/{workspaceId}` | [WS-02] 이름, 설명, 호스트, 멤버 수와 내 역할을 반환한다. 멤버가 아니면 조회할 수 없고, 없거나 삭제된 Workspace는 "찾을 수 없음"으로 안내한다. |
| Workspace 수정 | PATCH | `/api/workspaces/{workspaceId}` | [WS-03] 호스트가 이름·설명을 수정하며 전달된 필드만 바꾼다. 이름은 비울 수 없고, 변경은 「Workspace 변경 통지」로 모든 멤버 화면에 반영한다. |
| Workspace 삭제 | DELETE | `/api/workspaces/{workspaceId}` | [WS-04] 호스트가 Workspace와 하위 데이터를 삭제하며 복구할 수 없다. 진행 중인 투어와 미니게임은 서버가 자동으로 종료·중단한 뒤 삭제한다. |
| Workspace 변경 통지 | WS | `WORKSPACE_UPDATED` / `WORKSPACE_DELETED` | [WS-03·04 보조] 수정되면 바뀐 이름·설명을, 삭제되면 삭제 사실을 접속 중인 멤버 전원에게 보낸다. 삭제 통지를 받은 클라이언트는 목록 화면으로 이동한다. 전달 채널은 (확인 필요). |

## member

| 기능명 | Method | Endpoint | 설명 |
|---|---|---|---|
| 초대 링크 발급 | POST | `/api/workspaces/{workspaceId}/invitations` | [MEM-01] 호스트가 토큰이 든 초대 링크를 발급하며, 링크는 7일 후 만료된다. Workspace당 유효한 링크는 1개이고, 다시 발급하면 이전 링크는 바로 무효가 된다. |
| 현재 초대 링크 조회 | GET | `/api/workspaces/{workspaceId}/invitations/current` | [MEM-01 보조] 호스트에게 유효한 초대 링크와 만료 시각을 반환하고, 없으면 없음 상태를 반환한다. |
| 초대 정보 조회 | GET | `/api/invitations/{token}` | [MEM-02 보조] Workspace 이름, 호스트 닉네임, 멤버 수를 반환하고, 만료·무효 링크면 410을 반환한다. 로그인하지 않아도 조회할 수 있다. |
| 초대 수락 | POST | `/api/invitations/{token}/accept` | [MEM-02] 링크가 유효하면 멤버로 등록하고 Workspace ID를 반환한다. 이미 멤버면 같은 Workspace ID를 반환한다. 만료·무효 링크는 "만료된 초대 링크입니다"로 안내한다. |
| 멤버 목록 조회 | GET | `/api/workspaces/{workspaceId}/members` | [MEM-03] 멤버의 닉네임, 프로필 이미지, 역할(호스트/멤버), 참여 시각을 반환한다. 멤버가 아니면 조회할 수 없다. |
| 호스트 위임 | PATCH | `/api/workspaces/{workspaceId}/host` | [MEM-04] 호스트가 지정한 멤버를 새 호스트로 바꾸고 기존 호스트는 일반 멤버가 된다. 본인이나 멤버가 아닌 사람에게는 위임할 수 없다. |
| 멤버 내보내기 | DELETE | `/api/workspaces/{workspaceId}/members/{userId}` | [MEM-05] 호스트가 대상의 멤버 자격을 없애고 진행 중인 투어에서도 제외한다. 호스트 본인은 내보낼 수 없다. 대상의 채팅은 유지하고 투표는 집계에서 뺀다. |
| Workspace 나가기 | DELETE | `/api/workspaces/{workspaceId}/members/me` | [MEM-06] 본인의 멤버 자격을 없앤다. 호스트는 위임한 뒤에만 나갈 수 있다. 혼자 남은 호스트에게는 Workspace 삭제를 안내한다. |
| 멤버 변경 통지 | WS | `MEMBER_JOINED` / `MEMBER_LEFT` / `MEMBER_KICKED` / `HOST_CHANGED` | [MEM 보조] 접속 중인 멤버 전원에게 보내 목록을 갱신하게 한다. 내보내진 당사자에게도 MEMBER_KICKED를 보내 목록 화면으로 이동시킨다. 전달 채널은 「Workspace 변경 통지」와 같다. |

## chat

| 기능명 | Method | Endpoint | 설명 |
|---|---|---|---|
| 채팅 소켓 접속 | WS | `/ws/workspaces?workspaceId=` | [CHAT-01·02] Workspace 소켓을 연결하고 access token으로 인증한 뒤 멤버인지 검증한다. 채팅 메시지와 Workspace 실시간 동기화 이벤트를 이 연결로 주고받는다. 다시 연결되면 「대화 기록 조회」의 after로 놓친 메시지를 받는다. |
| 메시지 전송 | WS | `CHAT_SEND` | [CHAT-01] 클라이언트가 clientMessageId와 내용을 보내면 서버가 저장한 뒤 CHAT_MESSAGE로 멤버 전원에게 보낸다. 비어 있거나 공백뿐인 메시지는 거절하고, 같은 clientMessageId로 다시 보내면 새로 저장하지 않는다. |
| 메시지 수신 | WS | `CHAT_MESSAGE` | [CHAT-01] 저장된 메시지를 멤버 전원에게 보낸다. 보낸 사람도 받으며, clientMessageId로 전송 중이던 메시지를 확정 상태로 바꾼다. |
| 대화 기록 조회 | GET | `/api/workspaces/{workspaceId}/messages?before=&after=&size=` | [CHAT-02] 채팅 기록을 시간순 커서 페이지로 반환한다. before로 이전 기록을, after로 재연결 뒤 놓친 메시지를 받는다. 멤버가 아니면 조회할 수 없고, 기록이 없으면 빈 목록을 반환한다. |

## recommendation

| 기능명 | Method | Endpoint | 설명 |
|---|---|---|---|
| AI 분석 요청 | POST | `/api/workspaces/{workspaceId}/recommendations` | [REC-01] 대화 기록 분석을 시작하고 바로 접수 결과를 반환한다. 끝나면 「분석 진행 통지」로 알린다. 대화 기록이 없거나 이미 분석 중이면 시작할 수 없다. |
| 추천 위치 조회 | GET | `/api/workspaces/{workspaceId}/recommendations` | [REC-02] 가장 최근 분석의 상태와 추천 위치 목록(좌표, 추천 이유, 후보지 추가 여부)을 반환한다. 좌표를 얻지 못한 위치는 좌표를 null로 반환하고, 추천이 없으면 빈 목록을 반환한다. |
| 분석 진행 통지 | WS | `RECOMMENDATION_STARTED` / `RECOMMENDATION_COMPLETED` / `RECOMMENDATION_FAILED` | [REC-01 보조] 시작·완료·실패 사유를 Workspace 소켓 멤버 전원에게 보낸다. 완료 통지를 받으면 「추천 위치 조회」로 결과를 받는다. |
| LLM 위치 추천 | POST | `/api/ai/recommendations` | [REC-01] 대화 기록을 LLM으로 분석해 조건을 추출하고 추천 위치(좌표, 추천 이유)를 반환한다. 백엔드가 AI 서버를 호출하는 내부 API다. |
| 경사도 추정 | POST | `/api/ai/slope` | [EXT-01] 좌표 주변의 경사도 수치와 등급(완만/보통/가파름)을 반환한다. 공공데이터로 산출하고 로드뷰 이미지의 AI 분석으로 보완한다. 백엔드가 AI 서버를 호출하는 내부 API다. |
| 차량 진입 가능여부 추정 | POST | `/api/ai/vehicle-access` | [EXT-01] 좌표 주변 로드뷰 이미지를 AI로 분석해 차량 진입 가능 여부(가능/불가/정보 없음)와 근거를 반환한다. 백엔드가 AI 서버를 호출하는 내부 API다. |

## candidate

| 기능명 | Method | Endpoint | 설명 |
|---|---|---|---|
| 후보지 추가 | POST | `/api/workspaces/{workspaceId}/candidates` | [CAND-01] 추천 위치를 후보지로 추가하고 모든 멤버 화면에 실시간으로 반영한다. 이미 후보지로 추가된 위치는 중복으로 추가할 수 없다. |
| 후보지 조회 | GET | `/api/workspaces/{workspaceId}/candidates` | [CAND-02] 후보지 목록(위치, 확장 정보 요약, 투표 집계 요약)을 조회하고, 없으면 빈 상태를 안내한다. |
| 후보지 제외 | DELETE | `/api/candidates/{candidateId}` | [CAND-03] 후보지를 제외하고 연관된 투표를 삭제해 비교표에도 반영한다. 후보지를 추가한 멤버 또는 호스트만 제외할 수 있다. |

## comparison

| 기능명 | Method | Endpoint | 설명 |
|---|---|---|---|
| 비교표 생성 및 갱신 | POST | `/api/workspaces/{workspaceId}/comparisons` | [CMP-01] 후보지별 경사도, 일조량, 소음 위험 정보로 비교표를 생성한다. 후보지가 2개 미만이면 생성을 제한하고, 기존 비교표가 있으면 최신 정보로 갱신한다. |
| 비교표 조회 | GET | `/api/workspaces/{workspaceId}/comparisons` | [CMP-02] 생성된 후보지 비교표를 조회한다. 비교표가 없으면 생성을 안내한다. |

## vote

| 기능명 | Method | Endpoint | 설명 |
|---|---|---|---|
| 후보지 투표 | POST | `/api/candidates/{candidateId}/votes` | [VOTE-01] 찬성 또는 반대 투표를 등록하고 집계를 실시간으로 반영한다. 후보지당 멤버별 1표이고 이미 투표했으면 변경 기능을 이용한다. |
| 투표 변경 | PATCH | `/api/candidates/{candidateId}/votes/me` | [VOTE-03] 기존 투표를 변경한다. 실시간으로 반영되며, 다른 멤버의 투표 변경·투표 철회는 불가능하다. |
| 투표 결과 조회 | GET | `/api/workspaces/{workspaceId}/votes` | [VOTE-02] 후보지별 찬성·반대 수와 멤버별 투표 현황을 조회한다. 투표가 없는 후보지는 0표로 표시한다. |

## minigame

| 기능명 | Method | Endpoint | 설명 |
|---|---|---|---|
| 미니게임 시작 | POST | `/api/workspaces/{workspaceId}/minigames` | [GAME-01] 게임 종류(룰렛/가위바위보), 참여 멤버, 가위바위보의 방문자 기준(승자/패자)을 받아 게임을 생성하고 참여 멤버에게 알린다. 참여 멤버 2명 미만이거나 진행 중인 게임이 있으면 시작할 수 없다. |
| 진행 중인 미니게임 조회 | GET | `/api/workspaces/{workspaceId}/minigames/current` | [GAME-03 보조] 진행 중인 게임이 있으면 반환하고 없으면 '없음' 상태를 반환한다. |
| 미니게임 이력 조회 | GET | `/api/workspaces/{workspaceId}/minigames` | [GAME-03 보조] 종료된 게임 목록을 최신순으로 반환한다. |
| 미니게임 결과 조회 | GET | `/api/minigames/{gameId}` | [GAME-03] 게임 종류, 상태, 참여 멤버, 라운드 이력, 선정된 방문자를 반환한다. 결과가 확정되기 전에는 방문자를 null로 반환한다. |
| 룰렛 추첨 | POST | `/api/minigames/{gameId}/draw` | [GAME-02] 서버가 참여 멤버 중 무작위로 1명을 방문자로 확정한다. 룰렛 회전 연출 전에 결과가 노출되지 않게 시작과 분리한다. |
| 가위바위보 선택 제출 | POST | `/api/minigames/{gameId}/rounds/{roundNo}/choices` | [GAME-02] 라운드 선택을 제출한다. 전원이 제출하면 승패를 판정해 기준에 해당하는 멤버만 다음 라운드에 남기고 결과는 WebSocket으로 전달한다. 한 라운드에 중복 제출할 수 없다. |
| 미니게임 중단 | DELETE | `/api/minigames/{gameId}` | [GAME-04] 진행 중인 게임을 ABORTED로 바꾼다. 시작한 멤버가 호출하며, 참가자 이탈로 2명 미만이 되면 서버가 자동으로 중단한다. 종료된 게임의 결과는 삭제할 수 없다. |

## tour

| 기능명 | Method | Endpoint | 설명 |
|---|---|---|---|
| 실시간 투어 시작 | POST | `/api/workspaces/{workspaceId}/tours` | [TOUR-01] 미니게임으로 선정된 방문자가 투어 세션을 생성하고 시그널링 접속 정보를 받는다. 선정된 방문자가 아니거나 진행 중인 투어가 있으면 시작할 수 없다. 시작 시 멤버에게 알림을 발송한다. |
| 투어 상태·참여자 조회 | GET | `/api/workspaces/{workspaceId}/tours/current` | [TOUR-03] 진행 여부, 방문자, 참여자 목록과 연결 상태, 현재·최대 인원을 반환한다. 진행 중인 투어가 없으면 '없음' 상태를 반환한다. |
| 투어 이력 조회 | GET | `/api/workspaces/{workspaceId}/tours` | [TOUR-04 보조] 종료된 투어의 방문자, 시작·종료 시각 목록을 반환한다. |
| 실시간 투어 참여 | POST | `/api/tours/{tourId}/participants` | [TOUR-02] 진행 중인 투어에 참여자로 등록하고 시그널링 접속 정보를 받는다. 최대 인원을 초과하거나 진행 중이 아닌 투어에는 참여할 수 없다. |
| 투어 나가기 | DELETE | `/api/tours/{tourId}/participants/me` | [TOUR-04] 본인의 연결만 종료하고 퇴장 시각을 기록한다. 세션은 유지된다. |
| 투어 종료 | DELETE | `/api/tours/{tourId}` | [TOUR-04] 방문자가 세션을 종료한다. 모든 참여자의 연결이 끊기고 종료 시각이 기록된다. 방문자가 아닌 참여자는 종료할 수 없다. |
| 방문자 투어 재참여 | POST | `/api/tours/{tourId}/rejoin` | [TOUR-05] 접속이 끊긴 방문자가 진행 중인 세션에 다시 들어온다. 해당 세션의 방문자만 호출할 수 있다. |
| ICE 서버 정보 조회 | GET | `/api/tours/{tourId}/ice-servers` | [TOUR-02 보조] STUN·TURN 서버 주소와 시간 제한 자격증명을 요청 시 단기 발급한다. |

## signaling

| 기능명 | Method | Endpoint | 설명 |
|---|---|---|---|
| 시그널링 접속 | WS | `/ws/signaling?tourId=` | 연결 후 토큰으로 인증하고 해당 투어의 참여자인지 검증한다. 입장 시 JOIN을 보내고 JOINED로 기존 참여자 목록과 방문자 ID를 받는다. |
| 연결 협상 메시지 중계 | WS | `OFFER` / `ANSWER` / `ICE_CANDIDATE` | SDP offer·answer와 ICE 후보를 지정한 상대에게만 전달한다. 브로드캐스트하지 않고 저장하지 않는다. |
| 참여자 입·퇴장 통지 | WS | `PEER_JOINED` / `PEER_LEFT` | 참여자가 들어오거나 나가면 방 안에 통지한다. 방문자는 PEER_JOINED를 받아 새 참여자에게만 offer를 생성한다. |
| 연결 상태 보고 | WS | `CONNECTION_STATE` | 클라이언트가 연결 상태(CONNECTING/CONNECTED/DISCONNECTED)를 보고한다. 서버는 TOUR-03 조회 결과와 '방문자 연결 끊김' 표시에 사용한다. |
| 미니게임 진행 통지 | WS | `GAME_STARTED` / `ROUND_OPENED` / `ROUND_RESULT` / `GAME_FINISHED` | 게임 시작, 라운드 개시, 라운드 결과와 탈락자, 최종 방문자를 참여 멤버 전원에게 동시에 전달한다. |
| 투어 종료 통지 | WS | `TOUR_ENDED` | 방문자가 투어를 종료하거나 Workspace가 삭제되면 모든 참여자에게 종료를 알리고 연결을 정리하도록 한다. |

## notification

| 기능명 | Method | Endpoint | 설명 |
|---|---|---|---|
| 알림 소켓 접속 | WS | `/ws/notifications` | [NOTI-01] 연결 후 access token으로 인증한다. 같은 회원의 여러 탭·기기 연결에 모두 전달하되 알림은 한 번만 기록한다. 토큰이 만료되면 서버가 연결을 닫는다. |
| 새 알림 통지 | WS | `NOTIFICATION_CREATED` | [NOTI-01] 새 알림과 읽지 않은 개수를 접속 중인 대상에게 보내고, 접속 중이 아니면 웹 푸시로 보낸다. 행위자 본인에게는 보내지 않는다. 채팅 알림은 Workspace별로 읽지 않은 1건에 묶는다. |
| 읽음 상태 동기화 | WS | `NOTIFICATION_READ` | [NOTI-03 보조] 읽음 처리하면 같은 회원의 다른 연결에 읽은 알림 ID와 읽지 않은 개수를 보낸다. |
| 보고 있는 Workspace 보고 | WS | `VIEWING_WORKSPACE` | [NOTI-01 보조] 클라이언트가 보고 있는 Workspace ID(없으면 null)를 보낸다. 보고 있으면 새 채팅 알림을 보내지 않는다. 채팅 소켓 구독으로 판단할 수 있으면 생략한다. |
| 알림함 조회 | GET | `/api/notifications?cursor=&size=&unreadOnly=` | [NOTI-02] 내 알림을 최신순 커서 페이지로 반환하고 읽지 않은 개수를 함께 준다. 나간 Workspace의 알림은 빼고, 삭제된 리소스의 알림은 링크를 비활성으로 표시한다. 2주가 지난 알림은 반환하지 않는다. |
| 읽지 않은 알림 개수 조회 | GET | `/api/notifications/unread-count` | [NOTI-02 보조] 헤더 배지에 쓸 읽지 않은 개수만 반환한다. |
| 알림 읽음 처리 | PATCH | `/api/notifications/{notificationId}/read` | [NOTI-03] 알림 하나를 읽음으로 바꾸고 읽지 않은 개수를 반환한다. 이미 읽은 알림은 상태를 바꾸지 않는다. |
| 전체 읽음 처리 | PATCH | `/api/notifications/read-all` | [NOTI-03] 내 읽지 않은 알림을 모두 읽음으로 바꾸고 바뀐 개수를 반환한다. |
| 웹 푸시 공개키 조회 | GET | `/api/push-subscriptions/vapid-public-key` | [NOTI-04 보조] 서비스 워커가 구독을 만들 때 쓰는 VAPID 공개키를 반환한다. |
| 웹 푸시 구독 등록 | POST | `/api/push-subscriptions` | [NOTI-04] 구독 정보를 회원·기기 단위로 저장하고, 같은 endpoint가 있으면 갱신한다. 다른 회원의 endpoint면 현재 회원으로 옮긴다. |
| 웹 푸시 구독 해제 | DELETE | `/api/push-subscriptions?endpoint=` | [NOTI-05] 지정한 endpoint의 구독을 삭제한다. 본인 구독만 해제할 수 있고, 로그아웃할 때도 현재 기기의 구독을 해제한다. |
