package com.mockdata.zipfit.auth.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mockdata.zipfit.auth.entity.OAuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 구글 OAuth 2.0 (OpenID Connect). https://developers.google.com/identity/protocols/oauth2/web-server
 * 프론트는 scope=openid email profile 로 인가 코드를 받아 전달한다.
 * 구글은 토큰을 저장하지 않으므로 탈퇴 시 연결 끊기(revoke)는 하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class GoogleOAuthClient implements OAuthClient {

	private static final String AUTHORIZE_URI = "https://accounts.google.com/o/oauth2/v2/auth";
	private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
	private static final String USER_INFO_URI = "https://openidconnect.googleapis.com/v1/userinfo";

	private final OAuthProperties properties;
	private final OAuthApiCaller apiCaller;
	private final RestClient restClient = RestClient.create();

	@Override
	public OAuthProvider provider() {
		return OAuthProvider.GOOGLE;
	}

	@Override
	public String authorizeUrl() {
		return UriComponentsBuilder.fromUriString(AUTHORIZE_URI)
			.queryParam("client_id", properties.google().clientId())
			.queryParam("redirect_uri", properties.google().redirectUri())
			.queryParam("response_type", "code")
			.queryParam("scope", "openid email profile")
			.encode()
			.toUriString();
	}

	@Override
	public OAuthUserInfo fetchUserInfo(String code) {
		OAuthProperties.Google google = properties.google();
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", google.clientId());
		form.add("client_secret", google.clientSecret());
		form.add("redirect_uri", google.redirectUri());
		form.add("code", code);

		TokenResponse token = apiCaller.call("구글 토큰 발급", () -> restClient.post()
			.uri(TOKEN_URI)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(String.class), TokenResponse.class);

		UserInfoResponse user = apiCaller.call("구글 사용자 정보 조회", () -> restClient.get()
			.uri(USER_INFO_URI)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token.accessToken())
			.retrieve()
			.body(String.class), UserInfoResponse.class);

		return new OAuthUserInfo(
			OAuthProvider.GOOGLE,
			user.sub(),
			Boolean.TRUE.equals(user.emailVerified()) ? user.email() : null,
			user.name(),
			user.picture()
		);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TokenResponse(@JsonProperty("access_token") String accessToken) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record UserInfoResponse(
		String sub,
		String email,
		@JsonProperty("email_verified") Boolean emailVerified,
		String name,
		String picture
	) {
	}
}
