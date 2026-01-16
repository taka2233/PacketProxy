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
 * HSTS (Strict-Transport-Security) check. Validates that HSTS header is
 * present.
 */
public class HstsCheck implements SecurityCheck {

	@Override
	public String getName() {
		return "HSTS";
	}

	@Override
	public String getColumnName() {
		return "HSTS";
	}

	@Override
	public String getMissingMessage() {
		return "Strict-Transport-Security header is missing";
	}

	@Override
	public SecurityCheckResult check(HttpHeader header, Map<String, Object> context) {
		String hsts = header.getValue("Strict-Transport-Security").orElse("");

		if (!hsts.isEmpty()) {
			return SecurityCheckResult.ok("Strict-Transport-Security: " + hsts, hsts);
		} else {
			return SecurityCheckResult.fail("Missing", "");
		}
	}

	@Override
	public boolean matchesHeaderLine(String headerLine) {
		return headerLine.startsWith("strict-transport-security:");
	}
}
