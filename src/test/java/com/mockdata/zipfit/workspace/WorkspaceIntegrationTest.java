package com.mockdata.zipfit.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mockdata.zipfit.auth.AuthTestSupport;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.workspace.entity.Invitation;
import com.mockdata.zipfit.workspace.repository.InvitationRepository;
import com.mockdata.zipfit.workspace.repository.WorkspaceRepository;
import com.mockdata.zipfit.workspace.service.WorkspaceEventTypes;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

/** WS-01~04, MEM-01~06 */
@RecordApplicationEvents
class WorkspaceIntegrationTest extends AuthTestSupport {

	@Autowired
	WorkspaceRepository workspaceRepository;
	@Autowired
	InvitationRepository invitationRepository;
	@Autowired
	ApplicationEvents events;

	@Test
	@DisplayName("WS-01 생성자가 호스트가 되고, 이름이 비었거나 30자를 넘으면 만들 수 없다")
	void create() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();

		mockMvc.perform(json(bearer(post("/api/workspaces"), host), "{\"name\":\" 우리집 \",\"description\":\"성수동 투룸\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("우리집"))
			.andExpect(jsonPath("$.description").value("성수동 투룸"))
			.andExpect(jsonPath("$.host.nickname").value("호스트"))
			.andExpect(jsonPath("$.memberCount").value(1))
			.andExpect(jsonPath("$.myRole").value("HOST"));

		mockMvc.perform(json(bearer(post("/api/workspaces"), host), "{\"name\":\" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("name"));
		mockMvc.perform(json(bearer(post("/api/workspaces"), host), "{\"name\":\"%s\"}".formatted("가".repeat(31))))
			.andExpect(status().isBadRequest());
		mockMvc.perform(json(post("/api/workspaces"), "{\"name\":\"로그인안함\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("WS-02 내가 속한 Workspace만 최근 활동순으로 보이고, 멤버가 아니면 상세를 볼 수 없다")
	void listAndDetail() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		String guest = signupLocal("guest@zipfit.com", "password1", "손님").accessToken();
		Long first = createWorkspace(host, "첫번째");
		Long second = createWorkspace(host, "두번째");
		join(guest, issueInvitation(host, first));

		// 멤버가 들어온 첫번째가 최근 활동이다.
		mockMvc.perform(bearer(get("/api/workspaces"), host))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(first))
			.andExpect(jsonPath("$.items[0].memberCount").value(2))
			.andExpect(jsonPath("$.items[0].myRole").value("HOST"))
			.andExpect(jsonPath("$.items[1].id").value(second));
		mockMvc.perform(bearer(get("/api/workspaces"), guest))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].myRole").value("MEMBER"));

		mockMvc.perform(bearer(get("/api/workspaces/" + first), guest))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.host.nickname").value("호스트"))
			.andExpect(jsonPath("$.myRole").value("MEMBER"));
		mockMvc.perform(bearer(get("/api/workspaces/" + second), guest))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(ErrorCode.NOT_WORKSPACE_MEMBER.name()));
		mockMvc.perform(bearer(get("/api/workspaces/9999"), guest))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorCode.WORKSPACE_NOT_FOUND.name()));
	}

	@Test
	@DisplayName("WS-03 호스트만 전달된 필드를 수정하고, 접속 중인 멤버에게 알린다")
	void update() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		String guest = signupLocal("guest@zipfit.com", "password1", "손님").accessToken();
		Long id = createWorkspace(host, "이름");
		join(guest, issueInvitation(host, id));

		mockMvc.perform(json(bearer(patch("/api/workspaces/" + id), guest), "{\"name\":\"바꿈\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(ErrorCode.NOT_WORKSPACE_HOST.name()));
		mockMvc.perform(json(bearer(patch("/api/workspaces/" + id), host), "{\"name\":\"  \"}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(json(bearer(patch("/api/workspaces/" + id), host), "{\"description\":\"설명 추가\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("이름"))
			.andExpect(jsonPath("$.description").value("설명 추가"));
		mockMvc.perform(json(bearer(patch("/api/workspaces/" + id), host), "{\"name\":\"새 이름\",\"description\":\"\"}"))
			.andExpect(jsonPath("$.name").value("새 이름"))
			.andExpect(jsonPath("$.description").doesNotExist());

		assertThat(eventTypes(id)).contains(WorkspaceEventTypes.WORKSPACE_UPDATED);
	}

	@Test
	@DisplayName("MEM-01·02 초대 링크는 하나만 유효하고, 로그인 없이 정보를 본 뒤 수락하면 멤버가 된다")
	void invitationFlow() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		String guest = signupLocal("guest@zipfit.com", "password1", "손님").accessToken();
		Long id = createWorkspace(host, "초대방");

		mockMvc.perform(bearer(get("/api/workspaces/%d/invitations/current".formatted(id)), host))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.invitation").value((Object) null));
		String oldToken = issueInvitation(host, id);
		String token = issueInvitation(host, id);
		mockMvc.perform(bearer(get("/api/workspaces/%d/invitations/current".formatted(id)), host))
			.andExpect(jsonPath("$.invitation.token").value(token))
			.andExpect(jsonPath("$.invitation.expiresAt").exists());
		mockMvc.perform(get("/api/invitations/" + oldToken))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVITATION_EXPIRED.name()))
			.andExpect(jsonPath("$.message").value("만료된 초대 링크입니다."));

		mockMvc.perform(get("/api/invitations/" + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.workspaceName").value("초대방"))
			.andExpect(jsonPath("$.hostNickname").value("호스트"))
			.andExpect(jsonPath("$.memberCount").value(1));
		mockMvc.perform(post("/api/invitations/%s/accept".formatted(token)))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(bearer(post("/api/invitations/%s/accept".formatted(token)), guest))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.workspaceId").value(id))
			.andExpect(jsonPath("$.alreadyMember").value(false));
		mockMvc.perform(bearer(post("/api/invitations/%s/accept".formatted(token)), guest))
			.andExpect(jsonPath("$.workspaceId").value(id))
			.andExpect(jsonPath("$.alreadyMember").value(true));
		assertThat(eventTypes(id)).containsOnlyOnce(WorkspaceEventTypes.MEMBER_JOINED);

		// 호스트가 아니면 발급·조회할 수 없다.
		mockMvc.perform(bearer(post("/api/workspaces/%d/invitations".formatted(id)), guest))
			.andExpect(status().isForbidden());
		mockMvc.perform(bearer(get("/api/workspaces/%d/invitations/current".formatted(id)), guest))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("MEM-02 만료된 초대 링크로는 참여할 수 없다")
	void expiredInvitation() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		String guest = signupLocal("guest@zipfit.com", "password1", "손님").accessToken();
		Long id = createWorkspace(host, "만료방");
		invitationRepository.save(new Invitation(workspaceRepository.findById(id).orElseThrow(), "expired-token",
			Instant.now().minusSeconds(1)));

		mockMvc.perform(bearer(post("/api/invitations/expired-token/accept"), guest))
			.andExpect(status().isGone());
		mockMvc.perform(bearer(get("/api/workspaces/%d/invitations/current".formatted(id)), host))
			.andExpect(jsonPath("$.invitation").value((Object) null));
	}

	@Test
	@DisplayName("MEM-03·04 멤버 목록을 참여순으로 보고, 호스트를 위임하면 기존 호스트는 일반 멤버가 된다")
	void membersAndDelegation() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		String guest = signupLocal("guest@zipfit.com", "password1", "손님").accessToken();
		String outsider = signupLocal("out@zipfit.com", "password1", "외부인").accessToken();
		Long id = createWorkspace(host, "위임방");
		join(guest, issueInvitation(host, id));
		Long hostId = userIdOf("host@zipfit.com");
		Long guestId = userIdOf("guest@zipfit.com");
		Long outsiderId = userIdOf("out@zipfit.com");

		mockMvc.perform(bearer(get("/api/workspaces/%d/members".formatted(id)), guest))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].nickname").value("호스트"))
			.andExpect(jsonPath("$.items[0].role").value("HOST"))
			.andExpect(jsonPath("$.items[1].nickname").value("손님"))
			.andExpect(jsonPath("$.items[1].role").value("MEMBER"))
			.andExpect(jsonPath("$.items[1].joinedAt").exists());
		mockMvc.perform(bearer(get("/api/workspaces/%d/members".formatted(id)), outsider))
			.andExpect(status().isForbidden());

		delegate(host, id, hostId).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.CANNOT_DELEGATE_TO_SELF.name()));
		delegate(host, id, outsiderId).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorCode.MEMBER_NOT_FOUND.name()));
		delegate(guest, id, guestId).andExpect(status().isForbidden());

		delegate(host, id, guestId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.host.nickname").value("손님"))
			.andExpect(jsonPath("$.myRole").value("MEMBER"));
		assertThat(eventTypes(id)).contains(WorkspaceEventTypes.HOST_CHANGED);
		// 이제 기존 호스트는 호스트 기능을 쓸 수 없다.
		mockMvc.perform(bearer(post("/api/workspaces/%d/invitations".formatted(id)), host))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("MEM-05 내보낸 멤버는 접근할 수 없고, 그전에 발급한 초대 링크도 무효가 된다")
	void kick() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		String guest = signupLocal("guest@zipfit.com", "password1", "손님").accessToken();
		Long id = createWorkspace(host, "내보내기방");
		String token = issueInvitation(host, id);
		join(guest, token);
		Long hostId = userIdOf("host@zipfit.com");
		Long guestId = userIdOf("guest@zipfit.com");

		mockMvc.perform(bearer(delete("/api/workspaces/%d/members/%d".formatted(id, hostId)), host))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.CANNOT_KICK_HOST.name()));
		mockMvc.perform(bearer(delete("/api/workspaces/%d/members/%d".formatted(id, hostId)), guest))
			.andExpect(status().isForbidden());

		mockMvc.perform(bearer(delete("/api/workspaces/%d/members/%d".formatted(id, guestId)), host))
			.andExpect(status().isNoContent());
		mockMvc.perform(bearer(get("/api/workspaces/" + id), guest))
			.andExpect(status().isForbidden());
		mockMvc.perform(bearer(post("/api/invitations/%s/accept".formatted(token)), guest))
			.andExpect(status().isGone());
		assertThat(eventTypes(id)).contains(WorkspaceEventTypes.MEMBER_KICKED);
	}

	@Test
	@DisplayName("MEM-06 멤버는 나갈 수 있고, 호스트는 위임 후에만, 혼자면 삭제로 안내한다")
	void leave() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		String guest = signupLocal("guest@zipfit.com", "password1", "손님").accessToken();
		Long id = createWorkspace(host, "나가기방");
		join(guest, issueInvitation(host, id));

		mockMvc.perform(bearer(delete("/api/workspaces/%d/members/me".formatted(id)), host))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.HOST_CANNOT_LEAVE.name()));
		mockMvc.perform(bearer(delete("/api/workspaces/%d/members/me".formatted(id)), guest))
			.andExpect(status().isNoContent());
		assertThat(eventTypes(id)).contains(WorkspaceEventTypes.MEMBER_LEFT);

		mockMvc.perform(bearer(delete("/api/workspaces/%d/members/me".formatted(id)), host))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.LAST_MEMBER_CANNOT_LEAVE.name()));
	}

	@Test
	@DisplayName("WS-04 호스트가 삭제하면 멤버 모두 접근할 수 없고 삭제 사실을 알린다")
	void deleteWorkspace() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		String guest = signupLocal("guest@zipfit.com", "password1", "손님").accessToken();
		Long id = createWorkspace(host, "삭제방");
		String token = issueInvitation(host, id);
		join(guest, token);

		mockMvc.perform(bearer(delete("/api/workspaces/" + id), guest))
			.andExpect(status().isForbidden());
		mockMvc.perform(bearer(delete("/api/workspaces/" + id), host))
			.andExpect(status().isNoContent());

		mockMvc.perform(bearer(get("/api/workspaces/" + id), guest))
			.andExpect(status().isNotFound());
		mockMvc.perform(bearer(get("/api/workspaces"), guest))
			.andExpect(jsonPath("$.items.length()").value(0));
		mockMvc.perform(get("/api/invitations/" + token))
			.andExpect(status().isGone());
		assertThat(eventTypes(id)).contains(WorkspaceEventTypes.WORKSPACE_DELETED);
	}

	@Test
	@DisplayName("AUTH-06 다른 멤버가 있는 Workspace의 호스트는 위임 전에 탈퇴할 수 없다")
	void withdrawBlockedForHostWithMembers() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		String guest = signupLocal("guest@zipfit.com", "password1", "손님").accessToken();
		Long id = createWorkspace(host, "탈퇴방");
		join(guest, issueInvitation(host, id));

		mockMvc.perform(bearer(delete("/api/users/me"), host))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.HOST_DELEGATION_REQUIRED.name()));
		assertThat(userRepository.count()).isEqualTo(2);

		// 손님은 탈퇴하면 멤버에서 빠진다.
		mockMvc.perform(bearer(delete("/api/users/me"), guest))
			.andExpect(status().isNoContent());
		mockMvc.perform(bearer(get("/api/workspaces/" + id), host))
			.andExpect(jsonPath("$.memberCount").value(1));

		// 혼자 남은 호스트가 탈퇴하면 Workspace도 지운다.
		mockMvc.perform(bearer(delete("/api/users/me"), host))
			.andExpect(status().isNoContent());
		assertThat(workspaceRepository.count()).isZero();
	}

	private Long createWorkspace(String accessToken, String name) throws Exception {
		String body = mockMvc.perform(json(bearer(post("/api/workspaces"), accessToken), "{\"name\":\"%s\"}".formatted(name)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private String issueInvitation(String accessToken, Long workspaceId) throws Exception {
		String body = mockMvc.perform(bearer(post("/api/workspaces/%d/invitations".formatted(workspaceId)), accessToken))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	private void join(String accessToken, String token) throws Exception {
		mockMvc.perform(bearer(post("/api/invitations/%s/accept".formatted(token)), accessToken))
			.andExpect(status().isOk());
	}

	private ResultActions delegate(String accessToken, Long workspaceId, Long targetUserId) throws Exception {
		return mockMvc.perform(json(bearer(patch("/api/workspaces/%d/host".formatted(workspaceId)), accessToken),
			"{\"userId\":%d}".formatted(targetUserId)));
	}

	private Long userIdOf(String email) {
		return userRepository.findByEmailAndPasswordIsNotNull(email).orElseThrow().getId();
	}

	private List<String> eventTypes(Long workspaceId) {
		return events.stream(WorkspaceEvent.class)
			.filter(event -> event.workspaceId().equals(workspaceId))
			.map(WorkspaceEvent::type)
			.toList();
	}
}
