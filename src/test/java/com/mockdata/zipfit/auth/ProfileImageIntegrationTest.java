package com.mockdata.zipfit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mockdata.zipfit.auth.service.UserService;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.common.storage.LocalFileStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** AUTH-05 프로필 이미지 */
class ProfileImageIntegrationTest extends AuthTestSupport {

	private static final byte[] PNG = withHeader(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 64);
	private static final byte[] JPEG = withHeader(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, 64);

	@Autowired
	LocalFileStorage localFileStorage;

	@Test
	@DisplayName("이미지를 올리면 저장 후 로그인 없이 볼 수 있고, 바꾸면 이전 파일은 지운다")
	void uploadAndReplace() throws Exception {
		Tokens tokens = signupLocal("img@zipfit.com", "password1", "이미지");

		String firstUrl = urlOf(upload(tokens.accessToken(), "a.png", PNG).andExpect(status().isOk()));
		assertThat(firstUrl).startsWith("http://localhost:8080/uploads/profile-images/").endsWith(".png");
		mockMvc.perform(get(pathOf(firstUrl)))
			.andExpect(status().isOk())
			.andExpect(content().bytes(PNG));

		String secondUrl = urlOf(upload(tokens.accessToken(), "b.jpg", JPEG).andExpect(status().isOk()));
		assertThat(secondUrl).endsWith(".jpg");
		assertThat(fileOf(firstUrl)).doesNotExist();
		assertThat(fileOf(secondUrl)).exists();
	}

	@Test
	@DisplayName("이미지를 비우면 파일을 지우고 기본 이미지(null)로 되돌린다")
	void reset() throws Exception {
		Tokens tokens = signupLocal("reset-img@zipfit.com", "password1", "이미지삭제");
		String url = urlOf(upload(tokens.accessToken(), "a.png", PNG));

		mockMvc.perform(bearer(delete("/api/users/me/profile-image"), tokens.accessToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profileImageUrl").doesNotExist());
		assertThat(fileOf(url)).doesNotExist();
	}

	@Test
	@DisplayName("허용 형식이 아니거나 5MB를 넘으면 저장하지 않는다")
	void rejectInvalidImages() throws Exception {
		Tokens tokens = signupLocal("bad-img@zipfit.com", "password1", "잘못된이미지");

		// 확장자만 png인 텍스트 파일
		upload(tokens.accessToken(), "fake.png", "not an image".getBytes())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_IMAGE.name()));
		upload(tokens.accessToken(), "big.png", withHeader(PNG, (int) UserService.PROFILE_IMAGE_MAX_BYTES + 1))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.IMAGE_TOO_LARGE.name()));
		mockMvc.perform(bearer(multipart(HttpMethod.PUT, "/api/users/me/profile-image"), tokens.accessToken()))
			.andExpect(status().isBadRequest());

		mockMvc.perform(bearer(get("/api/users/me"), tokens.accessToken()))
			.andExpect(jsonPath("$.profileImageUrl").doesNotExist());
	}

	@Test
	@DisplayName("없는 경로는 500이 아니라 404로 응답한다")
	void unknownPathIs404() throws Exception {
		mockMvc.perform(get("/uploads/profile-images/nothing.png"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()));
	}

	private ResultActions upload(String accessToken, String filename, byte[] bytes) throws Exception {
		MockMultipartFile file = new MockMultipartFile("image", filename, "image/png", bytes);
		return mockMvc.perform(bearer(multipart(HttpMethod.PUT, "/api/users/me/profile-image").file(file), accessToken));
	}

	private static String urlOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.profileImageUrl");
	}

	private static String pathOf(String url) {
		return url.substring("http://localhost:8080".length());
	}

	private Path fileOf(String url) {
		return localFileStorage.getRoot().resolve(url.substring("http://localhost:8080/uploads/".length()));
	}

	private static byte[] withHeader(byte[] header, int size) {
		byte[] bytes = Arrays.copyOf(header, Math.max(size, header.length));
		return bytes;
	}

	static {
		// 테스트 업로드 폴더가 남아 있어도 상관없지만, 매번 깨끗하게 시작한다.
		try {
			Path dir = Path.of("build/test-uploads");
			if (Files.exists(dir)) {
				try (var paths = Files.walk(dir)) {
					paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
				}
			}
		} catch (Exception ignored) {
		}
	}
}
