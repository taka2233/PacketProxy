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
import packetproxy.http.HttpHeader;

public class CspCheckTest {

	private CspCheck check;
	private Map<String, Object> context;

	@BeforeEach
	public void setUp() {
		check = new CspCheck();
		context = new HashMap<>();
	}

	// ===== Missing Header Cases =====

	@Test
	public void testCheck_NoCspNoXfo_Fail() {
		HttpHeader header = TestHttpHeader.empty();
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isFail());
		assertEquals("(none)", result.getDisplayValue());
	}

	// ===== CSP without frame-ancestors =====

	@Test
	public void testCheck_CspWithoutFrameAncestors_Fail() {
		HttpHeader header = TestHttpHeader.withCsp("default-src 'self'");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isFail());
		assertEquals("default-src 'self'", result.getDisplayValue());
	}

	@Test
	public void testCheck_CspWithOnlyDefaultSrc_Fail() {
		HttpHeader header = TestHttpHeader.withCsp("default-src https:");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isFail());
	}

	@Test
	public void testCheck_CspWithScriptSrcOnly_Fail() {
		HttpHeader header = TestHttpHeader.withCsp("script-src 'self' 'unsafe-inline'");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isFail());
	}

	// ===== Malformed CSP Values =====

	@Test
	public void testCheck_CspWithEmptyValue_Fail() {
		HttpHeader header = TestHttpHeader.withCsp("");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isFail());
		assertEquals("(none)", result.getDisplayValue());
	}

	@Test
	public void testCheck_CspWithWhitespaceOnly_Fail() {
		HttpHeader header = TestHttpHeader.withCsp("   ");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isFail());
	}

	@Test
	public void testCheck_CspWithPartialFrameAncestors_Fail() {
		// "frame-ancestors" without proper value
		HttpHeader header = TestHttpHeader.withCsp("frame-ancestors");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isFail());
	}

	@Test
	public void testCheck_CspWithMisspelledFrameAncestors_Fail() {
		HttpHeader header = TestHttpHeader.withCsp("frame-ancestor 'self'");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isFail());
	}

	@Test
	public void testCheck_CspWithFrameAncestorsWrongQuotes_Fail() {
		// Double quotes instead of single quotes
		HttpHeader header = TestHttpHeader.withCsp("frame-ancestors \"self\"");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isFail());
	}

	// ===== X-Frame-Options Fallback =====

	@Test
	public void testCheck_XfoOnly_Ok() {
		HttpHeader header = TestHttpHeader.withXFrameOptions("DENY");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isOk());
		assertEquals("X-Frame-Options:DENY", result.getDisplayValue());
	}

	@Test
	public void testCheck_XfoSameorigin_Ok() {
		HttpHeader header = TestHttpHeader.withXFrameOptions("SAMEORIGIN");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isOk());
	}

	// ===== Valid CSP Cases =====

	@Test
	public void testCheck_CspWithFrameAncestorsNone_Ok() {
		HttpHeader header = TestHttpHeader.withCsp("frame-ancestors 'none'");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isOk());
		assertEquals("frame-ancestors 'none'", result.getDisplayValue());
	}

	@Test
	public void testCheck_CspWithFrameAncestorsSelf_Ok() {
		HttpHeader header = TestHttpHeader.withCsp("frame-ancestors 'self'");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isOk());
		assertEquals("frame-ancestors 'self'", result.getDisplayValue());
	}

	// ===== Context Storage =====

	@Test
	public void testCheck_StoresCspInContext() {
		String cspValue = "default-src 'self'; frame-ancestors 'none'";
		HttpHeader header = TestHttpHeader.withCsp(cspValue);
		check.check(header, context);

		assertEquals(cspValue, context.get(CspCheck.CONTEXT_KEY));
	}

	@Test
	public void testCheck_EmptyCsp_StoresEmptyInContext() {
		HttpHeader header = TestHttpHeader.empty();
		check.check(header, context);

		assertEquals("", context.get(CspCheck.CONTEXT_KEY));
	}

	// ===== matchesHeaderLine =====

	@Test
	public void testMatchesHeaderLine_CspHeader_True() {
		assertTrue(check.matchesHeaderLine("content-security-policy: default-src 'self'"));
	}

	@Test
	public void testMatchesHeaderLine_XfoHeader_True() {
		assertTrue(check.matchesHeaderLine("x-frame-options: DENY"));
	}

	@Test
	public void testMatchesHeaderLine_OtherHeader_False() {
		assertFalse(check.matchesHeaderLine("content-type: text/html"));
	}

	@Test
	public void testMatchesHeaderLine_EmptyString_False() {
		assertFalse(check.matchesHeaderLine(""));
	}

	@Test
	public void testMatchesHeaderLine_PartialMatch_False() {
		assertFalse(check.matchesHeaderLine("x-content-security-policy: default-src 'self'"));
	}

	// ===== Green Patterns =====

	@Test
	public void testGetGreenPatterns_ContainsFrameAncestorsNone() {
		assertTrue(check.getGreenPatterns().contains("frame-ancestors 'none'"));
	}

	@Test
	public void testGetGreenPatterns_ContainsFrameAncestorsSelf() {
		assertTrue(check.getGreenPatterns().contains("frame-ancestors 'self'"));
	}
}
