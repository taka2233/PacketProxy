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
import packetproxy.extensions.securityheaders.checks.HstsCheck;
import packetproxy.http.HttpHeader;

public class HstsCheckTest {

	private HstsCheck check;
	private Map<String, Object> context;

	@BeforeEach
	public void setUp() {
		check = new HstsCheck();
		context = new HashMap<>();
	}

	// ===== Missing Header Cases =====

	@Test
	public void testCheck_NoHstsHeader_Fail() {
		HttpHeader header = TestHttpHeader.empty();
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isFail());
		assertEquals("(none)", result.getDisplayValue());
	}

	@Test
	public void testCheck_EmptyHstsHeader_Fail() {
		// Empty value is treated as missing
		HttpHeader header = TestHttpHeader.withHsts("");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isFail());
	}

	@Test
	public void testCheck_HstsWithWhitespaceOnly_Fail() {
		HttpHeader header = TestHttpHeader.withHsts("   ");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isFail());
	}

	// ===== Malformed HSTS Values =====

	@Test
	public void testCheck_HstsWithInvalidDirective_Ok() {
		// The check doesn't validate directive format, just presence
		HttpHeader header = TestHttpHeader.withHsts("invalid-directive");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isOk());
	}

	@Test
	public void testCheck_HstsWithZeroMaxAge_Ok() {
		// max-age=0 effectively disables HSTS, but check passes
		HttpHeader header = TestHttpHeader.withHsts("max-age=0");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isOk());
	}

	@Test
	public void testCheck_HstsWithNegativeMaxAge_Ok() {
		// Invalid but check doesn't validate
		HttpHeader header = TestHttpHeader.withHsts("max-age=-1");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isOk());
	}

	// ===== Valid HSTS Cases =====

	@Test
	public void testCheck_HstsWithMaxAge_Ok() {
		HttpHeader header = TestHttpHeader.withHsts("max-age=31536000");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isOk());
		assertTrue(result.getDisplayValue().contains("max-age=31536000"));
	}

	@Test
	public void testCheck_HstsWithIncludeSubDomains_Ok() {
		HttpHeader header = TestHttpHeader.withHsts("max-age=31536000; includeSubDomains");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isOk());
	}

	@Test
	public void testCheck_HstsWithPreload_Ok() {
		HttpHeader header = TestHttpHeader.withHsts("max-age=31536000; includeSubDomains; preload");
		SecurityCheckResult result = check.check(header, context);

		assertTrue(result.isOk());
	}

	// ===== matchesHeaderLine =====

	@Test
	public void testMatchesHeaderLine_HstsHeader_True() {
		assertTrue(check.matchesHeaderLine("strict-transport-security: max-age=31536000"));
	}

	@Test
	public void testMatchesHeaderLine_OtherHeader_False() {
		assertFalse(check.matchesHeaderLine("content-security-policy: default-src 'self'"));
	}

	@Test
	public void testMatchesHeaderLine_EmptyString_False() {
		assertFalse(check.matchesHeaderLine(""));
	}

	@Test
	public void testMatchesHeaderLine_SimilarHeader_False() {
		assertFalse(check.matchesHeaderLine("x-strict-transport-security: max-age=31536000"));
	}

	// ===== Name and Column =====

	@Test
	public void testGetName() {
		assertEquals("HSTS", check.getName());
	}

	@Test
	public void testGetColumnName() {
		assertEquals("HSTS", check.getColumnName());
	}

	@Test
	public void testGetMissingMessage() {
		assertEquals("Strict-Transport-Security header is missing", check.getMissingMessage());
	}
}
