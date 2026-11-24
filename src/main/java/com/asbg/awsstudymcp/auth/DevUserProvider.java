package com.asbg.awsstudymcp.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DevUserProvider implements CurrentUserProvider {

	private final String devUserId;

	public DevUserProvider(@Value("${app.auth.dev-user-id:local-dev-user}") String devUserId) {
		this.devUserId = devUserId;
	}

	@Override
	public String currentUserId() {
		return devUserId;
	}

}
