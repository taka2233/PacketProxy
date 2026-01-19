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
package packetproxy.extensions.securityheaders.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import packetproxy.extensions.securityheaders.SecurityCheckResult;
import packetproxy.extensions.securityheaders.checks.CspCheck;
import packetproxy.extensions.securityheaders.checks.XssProtectionCheck;
import packetproxy.http.HttpHeader;

public class XssProtectionCheckTest {

	private XssProtectionCheck check;
	private Map<String, Object> context;

	@BeforeEach
	public void setUp() {
		check = new XssProtectionCheck();
		context = new HashMap<>();
	}

	// ===== No Protection at All =====

	@Test
	public void testCheck_NoXContentTypeOptionsNoCsp_Fail() {
		HttpHeader header = TestHttpHeader.empty();
		// Ensure CSP is not in context
		context.put(CspCheck.CONTEXT_KEY, "");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isFail());
		assertEquals("(none)", result.getDisplayValue());
	}

	@Test
	public void testCheck_NoCspInContext_Fail() {
		HttpHeader header = TestHttpHeader.empty();
		// No CSP key in context
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isFail());
	}

	// ===== Wrong X-Content-Type-Options Value =====

	@Test
	public void testCheck_XContentTypeOptionsWrongValue_Fail() {
		HttpHeader header = TestHttpHeader.withXContentTypeOptions("sniff");
		context.put(CspCheck.CONTEXT_KEY, "");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isFail());
	}

	@Test
	public void testCheck_XContentTypeOptionsEmptyValue_Fail() {
		HttpHeader header = TestHttpHeader.withXContentTypeOptions("");
		context.put(CspCheck.CONTEXT_KEY, "");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isFail());
	}

	@Test
	public void testCheck_XContentTypeOptionsPartialMatch_Fail() {
		// "nosniff-extra" is not "nosniff"
		HttpHeader header = TestHttpHeader.withXContentTypeOptions("nosniff-extra");
		context.put(CspCheck.CONTEXT_KEY, "");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isFail());
	}

	@Test
	public void testCheck_XContentTypeOptionsWithSpace_Ok() {
		// "nosniff " with trailing space - trimmed by HttpHeader
		HttpHeader header = TestHttpHeader.withXContentTypeOptions("nosniff ");
		context.put(CspCheck.CONTEXT_KEY, "");
		SecurityCheckResult result = check.check(header, context);

		// HttpHeader trims values, so this passes
		assertTrue(result.isOk());
	}

	// ===== X-Content-Type-Options: nosniff =====

	@Test
	public void testCheck_NosniffLowercase_Ok() {
		HttpHeader header = TestHttpHeader.withXContentTypeOptions("nosniff");
		context.put(CspCheck.CONTEXT_KEY, "");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isOk());
		assertEquals("nosniff", result.getDisplayValue());
	}

	@Test
	public void testCheck_NosniffUppercase_Ok() {
		HttpHeader header = TestHttpHeader.withXContentTypeOptions("NOSNIFF");
		context.put(CspCheck.CONTEXT_KEY, "");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isOk());
	}

	@Test
	public void testCheck_NosniffMixedCase_Ok() {
		HttpHeader header = TestHttpHeader.withXContentTypeOptions("NoSnIfF");
		context.put(CspCheck.CONTEXT_KEY, "");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isOk());
	}

	// ===== CSP as Alternative Protection =====

	@Test
	public void testCheck_BothNosniffAndCsp_Ok() {
		HttpHeader header = TestHttpHeader.withXContentTypeOptions("nosniff");
		context.put(CspCheck.CONTEXT_KEY, "default-src 'self'");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isOk());
		// Should prefer showing nosniff over CSP
		assertEquals("nosniff", result.getDisplayValue());
	}

	// ===== matchesHeaderLine =====

	@Test
	public void testMatchesHeaderLine_XContentTypeOptions_True() {
		assertTrue(check.matchesHeaderLine("x-content-type-options: nosniff"));
	}

	@Test
	public void testMatchesHeaderLine_OtherHeader_False() {
		assertFalse(check.matchesHeaderLine("content-type: text/html"));
	}

	@Test
	public void testMatchesHeaderLine_XxssProtection_False() {
		// This check is for X-Content-Type-Options, not X-XSS-Protection
		assertFalse(check.matchesHeaderLine("x-xss-protection: 1; mode=block"));
	}

	@Test
	public void testMatchesHeaderLine_EmptyString_False() {
		assertFalse(check.matchesHeaderLine(""));
	}

	// ===== Name and Messages =====

	@Test
	public void testGetName() {
		assertEquals("XSS Protection", check.getName());
	}

	@Test
	public void testGetColumnName() {
		assertEquals("XSS Protection", check.getColumnName());
	}

	@Test
	public void testGetMissingMessage() {
		assertEquals("X-Content-Type-Options: nosniff is missing", check.getMissingMessage());
	}
}
