/*
 * Copyright 2026 DeNA Co., Ltd.
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
package packetproxy.gui

import java.nio.charset.Charset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import packetproxy.model.Packet
import packetproxy.util.CharSetUtility

class SpreadsheetCopyFormatterTest {

  private lateinit var formatter: SpreadsheetCopyFormatter
  private lateinit var charsetUtil: CharSetUtility

  @BeforeEach
  fun setUp() {
    formatter = SpreadsheetCopyFormatter()
    charsetUtil = CharSetUtility.getInstance()

    // Ensure we use a charset supported by both PacketProxy and the JVM.
    val availableCharsets = charsetUtil.availableCharSetList
    val charsetToUse =
      Charset.availableCharsets().keys.firstOrNull { charsetName ->
        availableCharsets.contains(charsetName) && Charset.isSupported(charsetName)
      }
        ?: availableCharsets.filter { it != "AUTO" && Charset.isSupported(it) }.firstOrNull()
        ?: Charset.defaultCharset().name()

    charsetUtil.setCharSet(charsetToUse)

    var actualCharset = charsetUtil.charSet
    var attempts = 0
    while ((actualCharset == "AUTO" || !Charset.isSupported(actualCharset)) && attempts < 5) {
      val supportedCharset =
        Charset.availableCharsets().keys.firstOrNull { charsetName ->
          availableCharsets.contains(charsetName) && Charset.isSupported(charsetName)
        } ?: Charset.defaultCharset().name()
      charsetUtil.setCharSet(supportedCharset)
      actualCharset = charsetUtil.charSet
      attempts++
    }
  }

  // ===== isStreamingCommunication Tests =====

  @Test
  fun testIsStreamingCommunication_EmptyList_ReturnsFalse() {
    val result = formatter.isStreamingCommunication(emptyList())

    assertFalse(result)
  }

  @Test
  fun testIsStreamingCommunication_SinglePacket_ReturnsFalse() {
    val packet = createPacket(Packet.Direction.CLIENT, 1L)
    val result = formatter.isStreamingCommunication(listOf(packet))

    assertFalse(result)
  }

  @Test
  fun testIsStreamingCommunication_TwoPacketsClientServer_ReturnsFalse() {
    val clientPacket = createPacket(Packet.Direction.CLIENT, 1L)
    val serverPacket = createPacket(Packet.Direction.SERVER, 1L)
    val result = formatter.isStreamingCommunication(listOf(clientPacket, serverPacket))

    assertFalse(result)
  }

  @Test
  fun testIsStreamingCommunication_TwoPacketsServerServer_ReturnsFalse() {
    val serverPacket1 = createPacket(Packet.Direction.SERVER, 1L)
    val serverPacket2 = createPacket(Packet.Direction.SERVER, 1L)
    val result = formatter.isStreamingCommunication(listOf(serverPacket1, serverPacket2))

    assertFalse(result)
  }

  @Test
  fun testIsStreamingCommunication_TwoPacketsClientClient_ReturnsTrue() {
    val clientPacket1 = createPacket(Packet.Direction.CLIENT, 1L)
    val clientPacket2 = createPacket(Packet.Direction.CLIENT, 1L)
    val result = formatter.isStreamingCommunication(listOf(clientPacket1, clientPacket2))

    assertTrue(result)
  }

  @Test
  fun testIsStreamingCommunication_ThreePackets_ReturnsTrue() {
    val packet1 = createPacket(Packet.Direction.CLIENT, 1L)
    val packet2 = createPacket(Packet.Direction.SERVER, 1L)
    val packet3 = createPacket(Packet.Direction.CLIENT, 1L)
    val result = formatter.isStreamingCommunication(listOf(packet1, packet2, packet3))

    assertTrue(result)
  }

  // ===== buildCopyData Tests (Request Direction) =====

  @Test
  fun testBuildCopyData_RequestDirection_NonStreaming_ReturnsAllFields() {
    val requestBytes = buildHttpRequest("POST", "/api/test", "example.com", "request body")
    val responseBytes = buildHttpResponse(200, "application/json", """{"result": "ok"}""")
    val requestPacket = createPacket(Packet.Direction.CLIENT, 1L, requestBytes, 443, true)
    val responsePacket = createPacket(Packet.Direction.SERVER, 1L, responseBytes)
    val groupPackets = listOf(requestPacket, responsePacket)

    val result =
      formatter.buildCopyData(
        selectedPacket = requestPacket,
        groupPackets = groupPackets,
        direction = Packet.Direction.CLIENT,
        isStreaming = false,
        charsetUtil = charsetUtil,
      )

    val parts = result.split("\t")
    assertEquals(4, parts.size)

    val method = unquote(parts[0])
    val url = unquote(parts[1])
    val postData = unquote(parts[2])
    val responseData = unquote(parts[3])
    assertEquals("POST", method)
    assertTrue(url.contains("https://example.com/api/test") || url.contains("example.com/api/test"))
    assertEquals("request body", postData)
    assertEquals("""{"result": "ok"}""", responseData)
  }

  @Test
  fun testBuildCopyData_RequestDirection_GetMethod_PostDataEmpty() {
    val requestBytes = buildHttpRequest("GET", "/api/test", "example.com", "")
    val responseBytes = buildHttpResponse(200, "application/json", """{"ok": true}""")
    val requestPacket = createPacket(Packet.Direction.CLIENT, 1L, requestBytes, 443, true)
    val responsePacket = createPacket(Packet.Direction.SERVER, 1L, responseBytes)
    val groupPackets = listOf(requestPacket, responsePacket)

    val result =
      formatter.buildCopyData(
        selectedPacket = requestPacket,
        groupPackets = groupPackets,
        direction = Packet.Direction.CLIENT,
        isStreaming = false,
        charsetUtil = charsetUtil,
      )

    val parts = result.split("\t")
    assertEquals(4, parts.size)
    val method = unquote(parts[0])
    val postData = unquote(parts[2])
    assertEquals("GET", method)
    assertEquals("", postData)
  }

  @Test
  fun testBuildCopyData_RequestDirection_ResponseStatusCode301_ReturnsStatusCode() {
    val requestBytes = buildHttpRequest("GET", "/redirect", "example.com", "")
    val responseBytes = buildHttpResponse(301, "text/html", "")
    val requestPacket = createPacket(Packet.Direction.CLIENT, 1L, requestBytes, 443, true)
    val responsePacket = createPacket(Packet.Direction.SERVER, 1L, responseBytes)
    val groupPackets = listOf(requestPacket, responsePacket)

    val result =
      formatter.buildCopyData(
        selectedPacket = requestPacket,
        groupPackets = groupPackets,
        direction = Packet.Direction.CLIENT,
        isStreaming = false,
        charsetUtil = charsetUtil,
      )

    val parts = result.split("\t")
    assertEquals(4, parts.size)
    val responseData = unquote(parts[3])
    assertEquals("301", responseData)
  }

  @Test
  fun testBuildCopyData_RequestDirection_ResponseHtmlContentType_ReturnsHtml() {
    val requestBytes = buildHttpRequest("GET", "/page", "example.com", "")
    val responseBytes = buildHttpResponse(200, "text/html", "<html></html>")
    val requestPacket = createPacket(Packet.Direction.CLIENT, 1L, requestBytes, 443, true)
    val responsePacket = createPacket(Packet.Direction.SERVER, 1L, responseBytes)
    val groupPackets = listOf(requestPacket, responsePacket)

    val result =
      formatter.buildCopyData(
        selectedPacket = requestPacket,
        groupPackets = groupPackets,
        direction = Packet.Direction.CLIENT,
        isStreaming = false,
        charsetUtil = charsetUtil,
      )

    val parts = result.split("\t")
    assertEquals(4, parts.size)
    val responseData = unquote(parts[3])
    assertEquals("HTML", responseData)
  }

  @Test
  fun testBuildCopyData_RequestDirection_ResponseBodyExceedsMaxLength_Truncated() {
    val body = "a".repeat(SpreadsheetCopyFormatter.MAX_BODY_LENGTH + 1000)
    val requestBytes = buildHttpRequest("GET", "/large", "example.com", "")
    val responseBytes = buildHttpResponse(200, "application/json", body)
    val requestPacket = createPacket(Packet.Direction.CLIENT, 1L, requestBytes, 443, true)
    val responsePacket = createPacket(Packet.Direction.SERVER, 1L, responseBytes)
    val groupPackets = listOf(requestPacket, responsePacket)

    val result =
      formatter.buildCopyData(
        selectedPacket = requestPacket,
        groupPackets = groupPackets,
        direction = Packet.Direction.CLIENT,
        isStreaming = false,
        charsetUtil = charsetUtil,
      )

    val parts = result.split("\t")
    assertEquals(4, parts.size)
    val responseData = unquote(parts[3])
    assertEquals(SpreadsheetCopyFormatter.MAX_BODY_LENGTH, responseData.length)
  }

  @Test
  fun testBuildCopyData_RequestDirection_Streaming_ResponseDataEmpty() {
    val requestBytes = buildHttpRequest("POST", "/api/test", "example.com", "request body")
    val requestPacket1 = createPacket(Packet.Direction.CLIENT, 1L, requestBytes, 443, true)
    val requestPacket2 = createPacket(Packet.Direction.CLIENT, 1L, requestBytes, 443, true)
    val groupPackets = listOf(requestPacket1, requestPacket2)

    val result =
      formatter.buildCopyData(
        selectedPacket = requestPacket1,
        groupPackets = groupPackets,
        direction = Packet.Direction.CLIENT,
        isStreaming = true,
        charsetUtil = charsetUtil,
      )

    val parts = result.split("\t")
    assertEquals(4, parts.size)
    assertTrue(parts[3].contains("\"\""))
  }

  @Test
  fun testBuildCopyData_RequestDirection_Streaming_ThreePackets_ResponseDataEmpty() {
    val requestBytes = buildHttpRequest("GET", "/stream", "example.com", "")
    val responseBytes = buildHttpResponse(200, "application/json", """{"chunk": 1}""")
    val requestPacket = createPacket(Packet.Direction.CLIENT, 1L, requestBytes, 443, true)
    val responsePacket1 = createPacket(Packet.Direction.SERVER, 1L, responseBytes)
    val responsePacket2 = createPacket(Packet.Direction.SERVER, 1L, responseBytes)
    val groupPackets = listOf(requestPacket, responsePacket1, responsePacket2)

    val result =
      formatter.buildCopyData(
        selectedPacket = requestPacket,
        groupPackets = groupPackets,
        direction = Packet.Direction.CLIENT,
        isStreaming = true,
        charsetUtil = charsetUtil,
      )

    val parts = result.split("\t")
    assertEquals(4, parts.size)
    assertTrue(parts[3].contains("\"\""))
  }

  // ===== buildCopyData Tests (Response Direction) =====

  @Test
  fun testBuildCopyData_ResponseDirection_Streaming_ResponseDataEmpty() {
    val requestBytes = buildHttpRequest("GET", "/stream", "example.com", "")
    val responseBytes = buildHttpResponse(200, "application/json", """{"chunk": 1}""")
    val requestPacket = createPacket(Packet.Direction.CLIENT, 1L, requestBytes, 443, true)
    val responsePacket1 = createPacket(Packet.Direction.SERVER, 1L, responseBytes)
    val responsePacket2 = createPacket(Packet.Direction.SERVER, 1L, responseBytes)
    val groupPackets = listOf(requestPacket, responsePacket1, responsePacket2)

    val result =
      formatter.buildCopyData(
        selectedPacket = responsePacket1,
        groupPackets = groupPackets,
        direction = Packet.Direction.SERVER,
        isStreaming = true,
        charsetUtil = charsetUtil,
      )

    val parts = result.split("\t")
    assertEquals(4, parts.size)
    val method = unquote(parts[0])
    val url = unquote(parts[1])
    val responseData = unquote(parts[3])
    assertEquals("GET", method)
    assertTrue(url.contains("https://example.com/stream") || url.contains("example.com/stream"))
    assertEquals("", responseData)
  }

  @Test
  fun testBuildCopyData_ResponseDirection_WithRequest_ReturnsAllFields() {
    val requestBytes = buildHttpRequest("GET", "/api/test", "example.com", "")
    val responseBytes = buildHttpResponse(200, "application/json", """{"result": "ok"}""")
    val requestPacket = createPacket(Packet.Direction.CLIENT, 1L, requestBytes, 443, true)
    val responsePacket = createPacket(Packet.Direction.SERVER, 1L, responseBytes)
    val groupPackets = listOf(requestPacket, responsePacket)

    val result =
      formatter.buildCopyData(
        selectedPacket = responsePacket,
        groupPackets = groupPackets,
        direction = Packet.Direction.SERVER,
        isStreaming = false,
        charsetUtil = charsetUtil,
      )

    val parts = result.split("\t")
    assertEquals(4, parts.size)
    val method = unquote(parts[0])
    val url = unquote(parts[1])
    val responseData = unquote(parts[3])
    assertEquals("GET", method)
    assertTrue(url.contains("https://example.com/api/test") || url.contains("example.com/api/test"))
    assertEquals("""{"result": "ok"}""", responseData)
  }

  @Test
  fun testBuildCopyData_ResponseDirection_NoRequest_RequestFieldsEmpty() {
    val responseBytes = buildHttpResponse(200, "application/json", """{"result": "ok"}""")
    val responsePacket = createPacket(Packet.Direction.SERVER, 1L, responseBytes)
    val groupPackets = listOf(responsePacket)

    val result =
      formatter.buildCopyData(
        selectedPacket = responsePacket,
        groupPackets = groupPackets,
        direction = Packet.Direction.SERVER,
        isStreaming = false,
        charsetUtil = charsetUtil,
      )

    val parts = result.split("\t")
    assertEquals(4, parts.size)
    val method = unquote(parts[0])
    val url = unquote(parts[1])
    val postData = unquote(parts[2])
    val responseData = unquote(parts[3])
    assertEquals("", method)
    assertEquals("", url)
    assertEquals("", postData)
    assertEquals("""{"result": "ok"}""", responseData)
  }

  @Test
  fun testBuildCopyData_QuotesAndEscapesFieldsForTsv() {
    val requestBytes = buildHttpRequest("POST", "/api/test", "example.com", "a\"b")
    val responseBytes = buildHttpResponse(200, "application/json", """{"x":"y"}""")
    val requestPacket = createPacket(Packet.Direction.CLIENT, 1L, requestBytes, 443, true)
    val responsePacket = createPacket(Packet.Direction.SERVER, 1L, responseBytes)
    val groupPackets = listOf(requestPacket, responsePacket)

    val result =
      formatter.buildCopyData(
        selectedPacket = requestPacket,
        groupPackets = groupPackets,
        direction = Packet.Direction.CLIENT,
        isStreaming = false,
        charsetUtil = charsetUtil,
      )

    val parts = result.split("\t")
    assertEquals(4, parts.size)
    // Must be quoted and quotes escaped by doubling.
    assertNotNull(parts[2])
    assertTrue(parts[2].startsWith("\"") && parts[2].endsWith("\""))
    assertTrue(parts[2].contains("a\"\"b"))
  }

  // ===== Helper Methods =====

  private fun createPacket(
    direction: Packet.Direction,
    group: Long,
    decodedData: ByteArray = byteArrayOf(),
    serverPort: Int = 80,
    useSSL: Boolean = false,
  ): Packet {
    val packet =
      Packet(
        8080,
        "127.0.0.1",
        50000,
        "127.0.0.1",
        serverPort,
        "example.com",
        useSSL,
        "",
        "",
        direction,
        1,
        group,
      )
    packet.setDecodedData(decodedData)
    return packet
  }

  private fun buildHttpRequest(
    method: String,
    path: String,
    host: String,
    body: String = "",
  ): ByteArray {
    val bodyBytes = body.toByteArray(Charsets.UTF_8)
    val contentLength = bodyBytes.size
    val request =
      "$method $path HTTP/1.1\r\n" +
        "Host: $host\r\n" +
        "Content-Length: $contentLength\r\n" +
        "\r\n" +
        body
    return request.toByteArray(Charsets.UTF_8)
  }

  private fun buildHttpResponse(
    statusCode: Int,
    contentType: String,
    body: String = "",
  ): ByteArray {
    val bodyBytes = body.toByteArray(Charsets.UTF_8)
    val contentLength = bodyBytes.size
    val response =
      "HTTP/1.1 $statusCode OK\r\n" +
        "Content-Type: $contentType\r\n" +
        "Content-Length: $contentLength\r\n" +
        "\r\n" +
        body
    return response.toByteArray(Charsets.UTF_8)
  }

  private fun unquote(value: String): String {
    if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length - 1).replace("\"\"", "\"")
    }
    return value
  }
}
