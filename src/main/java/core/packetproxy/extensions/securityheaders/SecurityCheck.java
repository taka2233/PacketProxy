/*
 * Copyright 2019 DeNA Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package packetproxy.extensions.securityheaders;

import java.util.Map;
import packetproxy.http.HttpHeader;

/**
 * Interface for security header checks. Implement this interface to add custom
 * security header validation rules.
 */
public interface SecurityCheck {

	/**
	 * Get the display name for this check (shown in issues pane)
	 *
	 * @return Display name (e.g., "CSP", "XSS Protection")
	 */
	String getName();

	/**
	 * Get the column name for the table
	 *
	 * @return Column name (e.g., "CSP", "XSS")
	 */
	String getColumnName();

	/**
	 * Get the error message when this check fails
	 *
	 * @return Error message describing the issue
	 */
	String getMissingMessage();

	/**
	 * Perform the security check
	 *
	 * @param header
	 *            The HTTP response header to check
	 * @param context
	 *            Shared context for checks that depend on other checks' results
	 * @return The result of the check
	 */
	SecurityCheckResult check(HttpHeader header, Map<String, Object> context);

	/**
	 * Check if a header line matches this security check Used for color-coding
	 * headers in the display
	 *
	 * @param headerLine
	 *            The header line to check (lowercase)
	 * @return true if this check applies to the header line
	 */
	boolean matchesHeaderLine(String headerLine);

	/**
	 * Determine if this check affects the overall pass/fail status
	 *
	 * @return true if a failure should cause overall FAIL status
	 */
	default boolean affectsOverallStatus() {
		return true;
	}

	enum HighlightType {
		GREEN, RED, YELLOW, NONE
	}

	default HighlightType getHighlightType(String headerLine, SecurityCheckResult result) {
		if (!matchesHeaderLine(headerLine.toLowerCase())) {
			return HighlightType.NONE;
		}
		if (result != null) {
			if (result.isOk()) {
				return HighlightType.GREEN;
			}
			if (result.isWarn()) {
				return HighlightType.YELLOW;
			}
			if (result.isFail()) {
				return HighlightType.RED;
			}
		}
		return HighlightType.NONE;
	}
}
