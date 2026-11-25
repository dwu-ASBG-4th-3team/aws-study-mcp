package com.asbg.awsstudymcp.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class DevUserProvider implements CurrentUserProvider {

	private final String devUserId;

	public DevUserProvider(@Value("${app.auth.dev-user-id}") String devUserId) {
		this.devUserId = devUserId;
	}

	@Override
	public String currentUserId() {
		return devUserId;
	}

}
