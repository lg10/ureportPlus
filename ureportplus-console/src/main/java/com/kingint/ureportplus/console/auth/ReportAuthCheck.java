/*******************************************************************************
 * Copyright 2017 Bstek
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package com.kingint.ureportplus.console.auth;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;

import com.kingint.ureportplus.definition.ReportDefinition;

/**
 * Abstract authorization check for report access.
 * <p>
 * Consuming projects should extend this class, implement {@link #validateToken},
 * and register the implementation as a Spring bean.
 * If no implementation is registered, all auth-enabled reports will be rejected.
 * </p>
 *
 * @author Jacky.gao
 * @since 2025年3月10日
 */
public abstract class ReportAuthCheck {

	/**
	 * Validate the token and return verified parameter values.
	 * Implementation is entirely custom (JWT, session, database, OAuth, etc.).
	 *
	 * @param token     the token from the URL query parameter "token"
	 * @param reportDef the report definition (provides access to Paper.getAuthParams())
	 * @param request   the HTTP request (provides access to all query parameters, headers, session, etc.)
	 * @return a Map of verified parameter-name-to-value pairs.
	 *         Each key should match a name listed in Paper.getAuthParams().
	 *         Return null or empty map on validation failure.
	 */
	public abstract Map<String, String> validateToken(String token,
			ReportDefinition reportDef, HttpServletRequest request);

	/**
	 * Check that the request contains all auth parameters defined in
	 * Paper.getAuthParams() and that their values match the verified values
	 * returned by {@link #validateToken}.
	 *
	 * @param reportDef      the report definition
	 * @param request        the HTTP request
	 * @param verifiedParams the map returned by validateToken()
	 * @return true if all auth params are present and values match
	 */
	public boolean checkAuthParams(ReportDefinition reportDef,
			HttpServletRequest request, Map<String, String> verifiedParams) {
		if (!reportDef.getPaper().isAuthEnabled()) {
			return true;
		}
		String authParams = reportDef.getPaper().getAuthParams();
		if (StringUtils.isBlank(authParams)) {
			return true;
		}
		if (verifiedParams == null || verifiedParams.isEmpty()) {
			return false;
		}
		String[] names = authParams.split(",");
		for (String name : names) {
			name = name.trim();
			if (name.length() == 0) {
				continue;
			}
			String requestValue = request.getParameter(name);
			String verifiedValue = verifiedParams.get(name);
			if (requestValue == null || !requestValue.equals(verifiedValue)) {
				return false;
			}
		}
		return true;
	}
}
