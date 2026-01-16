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
 * Content-Type check. Validates that charset is specified for text/html
 * responses.
 */
public class ContentTypeCheck implements SecurityCheck {

	@Override
	public String getName() {
		return "Content-Type";
	}

	@Override
	public String getColumnName() {
		return "Content-Type";
	}

	@Override
	public String getMissingMessage() {
		return "Content-Type header is missing charset for text/html";
	}

	@Override
	public SecurityCheckResult check(HttpHeader header, Map<String, Object> context) {
		String contentType = header.getValue("Content-Type").orElse("");
		String lowerContentType = contentType.toLowerCase();

		// Only check charset for text/html
		if (lowerContentType.contains("text/html")) {
			if (lowerContentType.contains("charset=")) {
				return SecurityCheckResult.ok(contentType, contentType);
			} else {
				return SecurityCheckResult.fail("No charset", contentType);
			}
		}

		// For non-HTML content, just return OK
		return SecurityCheckResult.ok(contentType, contentType);
	}

	@Override
	public boolean matchesHeaderLine(String headerLine) {
		return headerLine.startsWith("content-type:");
	}
}
