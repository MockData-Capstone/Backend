package com.mockdata.zipfit.auth.oauth;

import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 제공자별 OAuth 클라이언트 조회 */
@Component
@RequiredArgsConstructor
public class OAuthClients {

	private final List<OAuthClient> clients;

	public OAuthClient get(OAuthProvider provider) {
		return find(provider).orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER));
	}

	public Optional<OAuthClient> find(OAuthProvider provider) {
		return clients.stream().filter(client -> client.provider() == provider).findFirst();
	}
}
