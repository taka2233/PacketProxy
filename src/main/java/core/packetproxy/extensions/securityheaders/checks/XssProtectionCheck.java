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
package packetproxy.extensions.securityheaders.checks;

import java.util.Map;
import packetproxy.extensions.securityheaders.SecurityCheck;
import packetproxy.extensions.securityheaders.SecurityCheckResult;
import packetproxy.http.HttpHeader;

/**
 * XSS Protection check (X-Content-Type-Options). Validates that nosniff is set
 * or CSP is present.
 */
public class XssProtectionCheck implements SecurityCheck {

	@Override
	public String getName() {
		return "XSS Protection";
	}

	@Override
	public String getColumnName() {
		return "XSS";
	}

	@Override
	public String getMissingMessage() {
		return "X-Content-Type-Options: nosniff is missing and no CSP";
	}

	@Override
	public SecurityCheckResult check(HttpHeader header, Map<String, Object> context) {
		String xContentTypeOptions = header.getValue("X-Content-Type-Options").orElse("");
		String csp = (String) context.getOrDefault(CspCheck.CONTEXT_KEY, "");

		boolean hasNosniff = xContentTypeOptions.equalsIgnoreCase("nosniff");
		boolean hasCsp = !csp.isEmpty();

		if (hasNosniff || hasCsp) {
			String displayValue = xContentTypeOptions.isEmpty() ? "CSP" : xContentTypeOptions;
			return SecurityCheckResult.ok(displayValue, xContentTypeOptions);
		} else {
			return SecurityCheckResult.fail("Missing", "");
		}
	}

	@Override
	public boolean matchesHeaderLine(String headerLine) {
		return headerLine.startsWith("x-content-type-options:");
	}
}
