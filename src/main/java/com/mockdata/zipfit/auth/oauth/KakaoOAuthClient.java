package com.mockdata.zipfit.auth.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mockdata.zipfit.auth.entity.OAuthProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/** 카카오 로그인 REST API. https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoOAuthClient implements OAuthClient {

	private static final String AUTHORIZE_URI = "https://kauth.kakao.com/oauth/authorize";
	private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
	private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
	private static final String UNLINK_URI = "https://kapi.kakao.com/v1/user/unlink";

	private final OAuthProperties properties;
	private final OAuthApiCaller apiCaller;
	private final RestClient restClient = RestClient.create();

	@Override
	public OAuthProvider provider() {
		return OAuthProvider.KAKAO;
	}

	@Override
	public String authorizeUrl() {
		return UriComponentsBuilder.fromUriString(AUTHORIZE_URI)
			.queryParam("client_id", properties.kakao().clientId())
			.queryParam("redirect_uri", properties.kakao().redirectUri())
			.queryParam("response_type", "code")
			.encode()
			.toUriString();
	}

	@Override
	public OAuthUserInfo fetchUserInfo(String code) {
		OAuthProperties.Kakao kakao = properties.kakao();
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", kakao.clientId());
		form.add("redirect_uri", kakao.redirectUri());
		form.add("code", code);
		if (StringUtils.hasText(kakao.clientSecret())) {
			form.add("client_secret", kakao.clientSecret());
		}

		TokenResponse token = apiCaller.call("카카오 토큰 발급", () -> restClient.post()
			.uri(TOKEN_URI)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(String.class), TokenResponse.class);

		UserResponse user = apiCaller.call("카카오 사용자 정보 조회", () -> restClient.get()
			.uri(USER_INFO_URI)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token.accessToken())
			.retrieve()
			.body(String.class), UserResponse.class);

		KakaoAccount account = user.kakaoAccount();
		Profile profile = account == null ? null : account.profile();
		boolean emailVerified = account != null
			&& Boolean.TRUE.equals(account.isEmailValid())
			&& Boolean.TRUE.equals(account.isEmailVerified());
		boolean customImage = profile != null && !Boolean.TRUE.equals(profile.isDefaultImage());

		return new OAuthUserInfo(
			OAuthProvider.KAKAO,
			String.valueOf(user.id()),
			emailVerified ? account.email() : null,
			profile == null ? null : profile.nickname(),
			customImage ? profile.profileImageUrl() : null
		);
	}

	@Override
	public void unlink(String providerUserId) {
		String adminKey = properties.kakao().adminKey();
		if (!StringUtils.hasText(adminKey)) {
			log.warn("카카오 어드민 키가 없어 연결 끊기를 건너뜁니다. kakaoUserId={}", providerUserId);
			return;
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("target_id_type", "user_id");
		form.add("target_id", providerUserId);
		try {
			restClient.post()
				.uri(UNLINK_URI)
				.header(HttpHeaders.AUTHORIZATION, "KakaoAK " + adminKey)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.toBodilessEntity();
		} catch (RestClientException e) {
			log.warn("카카오 연결 끊기 실패. kakaoUserId={}", providerUserId, e);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TokenResponse(@JsonProperty("access_token") String accessToken) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record UserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record KakaoAccount(
		Profile profile,
		String email,
		@JsonProperty("is_email_valid") Boolean isEmailValid,
		@JsonProperty("is_email_verified") Boolean isEmailVerified
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Profile(
		String nickname,
		@JsonProperty("profile_image_url") String profileImageUrl,
		@JsonProperty("is_default_image") Boolean isDefaultImage
	) {
	}
}
