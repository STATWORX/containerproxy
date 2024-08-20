/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2023 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.containerproxy.auth.impl;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer.AuthorizedUrl;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;

/**
 * External authentication: Another services handles the authentication and hands over the info via headers.
 */
// TODO: find a way to parse header info from upstream proxy
public class ExternalAuthenticationBackend implements IAuthenticationBackend {

	public static final String NAME = "external";
	

	@Autowired
	HttpServletRequest request;

	@Override
	public String getName() {
		return NAME;
	}

	public void getAuthHeaders() {
		String userName = request.getHeader("x-auth-user");
		String isAdmin = request.getHeader("x-auth-admin");
		String userId = request.getHeader("x-auth-user-id");
		String userRoles = request.getHeader("x-auth-roles");
		String userGroups = request.getHeader("x-auth-groups");
		String userToken = request.getHeader("x-auth-token");
	}
	
	@Override
	public boolean hasAuthorization() {
		return true;
	}
	
	@Override
	public void configureHttpSecurity(HttpSecurity http, AuthorizedUrl anyRequestConfigurer) throws Exception {
		// Nothing to do.
	}

	@Override
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
		// Configure a no-op authentication.
		auth.inMemoryAuthentication();
	}

}
