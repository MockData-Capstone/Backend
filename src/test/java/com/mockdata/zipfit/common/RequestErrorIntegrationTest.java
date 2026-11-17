package com.mockdata.zipfit.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mockdata.zipfit.auth.AuthTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 공통 오류 응답: 잘못된 HTTP 메서드·인증 없음·없는 경로·잘못된 본문·형식·타입은 500이 아니라 정해진 코드와 JSON으로 답한다. */
class RequestErrorIntegrationTest extends AuthTestSupport {

	@Test
	@DisplayName("지원하지 않는 HTTP 메서드는 405 METHOD_NOT_ALLOWED")
	void methodNotAllowed() throws Exception {
		String token = signupLocal("method@zipfit.com", "password1", "메서드").accessToken();
		MockHttpServletRequestBuilder[] requests = {
			put("/api/auth/login"),
			get("/api/auth/logout"),
			delete("/api/workspaces"),
			patch("/api/workspaces/1/recommendations"),
			put("/api/notifications/read-all"),
			post("/api/users/me"),
		};
		for (MockHttpServletRequestBuilder request : requests) {
			mockMvc.perform(bearer(request, token))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
		}
	}

	@Test
	@DisplayName("토큰 없이 보호된 API를 부르면 401, 잘못된 토큰도 401")
	void unauthorized() throws Exception {
		mockMvc.perform(get("/api/workspaces"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(get("/api/workspaces").header("Authorization", "Bearer not-a-token"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("없는 경로는 404 NOT_FOUND, 없는 Workspace는 404 WORKSPACE_NOT_FOUND")
	void notFound() throws Exception {
		String token = signupLocal("notfound@zipfit.com", "password1", "없는곳").accessToken();
		mockMvc.perform(bearer(get("/api/nope"), token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(bearer(get("/api/workspaces/999999"), token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
	}

	@Test
	@DisplayName("깨진 JSON은 400 INVALID_INPUT, JSON이 아닌 형식은 415")
	void badBody() throws Exception {
		String token = signupLocal("body@zipfit.com", "password1", "본문").accessToken();
		mockMvc.perform(bearer(post("/api/workspaces"), token).contentType(MediaType.APPLICATION_JSON).content("{bad"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		mockMvc.perform(bearer(post("/api/workspaces"), token).contentType(MediaType.TEXT_PLAIN).content("x"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	@DisplayName("경로 값의 타입이 틀리면 500이 아니라 400 INVALID_INPUT과 필드 이름")
	void typeMismatch() throws Exception {
		String token = signupLocal("type@zipfit.com", "password1", "타입").accessToken();
		mockMvc.perform(bearer(get("/api/workspaces/abc"), token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors[0].field").value("workspaceId"));
		mockMvc.perform(bearer(get("/api/candidates/x1"), token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}
}
