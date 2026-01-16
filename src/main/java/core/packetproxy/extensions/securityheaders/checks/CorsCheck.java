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
 * CORS check (Access-Control-Allow-Origin). Validates that wildcard (*) is not
 * used.
 */
public class CorsCheck implements SecurityCheck {

	@Override
	public String getName() {
		return "CORS";
	}

	@Override
	public String getColumnName() {
		return "CORS";
	}

	@Override
	public String getMissingMessage() {
		return "Access-Control-Allow-Origin is set to '*' (wildcard)";
	}

	@Override
	public SecurityCheckResult check(HttpHeader header, Map<String, Object> context) {
		String cors = header.getValue("Access-Control-Allow-Origin").orElse("");

		if (cors.isEmpty()) {
			return SecurityCheckResult.ok("No CORS", "");
		}

		if (cors.equals("*")) {
			return SecurityCheckResult.fail(cors, cors);
		}

		return SecurityCheckResult.ok(cors, cors);
	}

	@Override
	public boolean matchesHeaderLine(String headerLine) {
		return headerLine.startsWith("access-control-allow-origin:");
	}
}
