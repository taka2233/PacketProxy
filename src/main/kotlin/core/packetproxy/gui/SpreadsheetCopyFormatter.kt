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
import packetproxy.http.Http
import packetproxy.model.Packet
import packetproxy.util.CharSetUtility

/**
 * Builds tab-separated copy data for spreadsheet pasting.
 *
 * Output format (TSV with quoted fields): "Method" "URL" "POST data" "Response data"
 */
class SpreadsheetCopyFormatter {

  companion object {
    // Keep the same cap as the previous extension implementation.
    // (Prevents excessively large clipboard data and spreadsheet cell issues.)
    const val MAX_BODY_LENGTH = 49000
  }

  /**
   * Streaming if:
   * - 3 or more packets in the same group, OR
   * - 2 packets in the same group and both are requests (CLIENT)
   */
  fun isStreamingCommunication(groupPackets: List<Packet>): Boolean {
    if (groupPackets.size >= 3) return true
    if (groupPackets.size != 2) return false

    val requestCount = groupPackets.count { it.direction == Packet.Direction.CLIENT }
    return requestCount >= 2
  }

  private data class CopyFields(
    val method: String,
    val url: String,
    val postData: String,
    val responseData: String,
  )

  fun buildCopyData(
    selectedPacket: Packet,
    groupPackets: List<Packet>,
    direction: Packet.Direction,
    isStreaming: Boolean,
    charsetUtil: CharSetUtility,
  ): String {
    val fields =
      when (direction) {
        Packet.Direction.CLIENT ->
          buildFromRequest(
            selectedPacket = selectedPacket,
            groupPackets = groupPackets,
            charsetUtil = charsetUtil,
            isStreaming = isStreaming,
          )
        Packet.Direction.SERVER ->
          buildFromResponse(
            selectedPacket = selectedPacket,
            groupPackets = groupPackets,
            charsetUtil = charsetUtil,
            isStreaming = isStreaming,
          )
      }

    return listOf(fields.method, fields.url, fields.postData, fields.responseData).joinToString(
      "\t"
    ) {
      quoteForTsv(it)
    }
  }

  private fun buildFromRequest(
    selectedPacket: Packet,
    groupPackets: List<Packet>,
    charsetUtil: CharSetUtility,
    isStreaming: Boolean,
  ): CopyFields {
    val requestHttp = Http.create(selectedPacket.decodedData)
    if (charsetUtil.isAuto) {
      charsetUtil.setGuessedCharSet(requestHttp.body)
    }

    val method = requestHttp.method ?: ""
    val url = requestHttp.getURL(selectedPacket.serverPort, selectedPacket.useSSL) ?: ""
    val postData = toBodyString(requestHttp.body, charsetUtil).take(MAX_BODY_LENGTH)

    if (isStreaming) {
      return CopyFields(method = method, url = url, postData = postData, responseData = "")
    }

    val responseData =
      findFirstResponse(groupPackets)?.let { responsePacket ->
        val responseHttp = Http.create(responsePacket.decodedData)
        createResponseData(responseHttp, charsetUtil)
      } ?: ""

    return CopyFields(method = method, url = url, postData = postData, responseData = responseData)
  }

  private fun buildFromResponse(
    selectedPacket: Packet,
    groupPackets: List<Packet>,
    charsetUtil: CharSetUtility,
    isStreaming: Boolean,
  ): CopyFields {
    if (isStreaming) {
      val requestPacket = findFirstRequest(groupPackets) ?: return CopyFields("", "", "", "")
      val requestHttp = Http.create(requestPacket.decodedData)
      if (charsetUtil.isAuto) {
        charsetUtil.setGuessedCharSet(requestHttp.body)
      }
      val method = requestHttp.method ?: ""
      val url = requestHttp.getURL(requestPacket.serverPort, requestPacket.useSSL) ?: ""
      val postData = toBodyString(requestHttp.body, charsetUtil).take(MAX_BODY_LENGTH)
      return CopyFields(method = method, url = url, postData = postData, responseData = "")
    }

    val responseHttp = Http.create(selectedPacket.decodedData)
    val responseData = createResponseData(responseHttp, charsetUtil)

    val requestPacket =
      findFirstRequest(groupPackets) ?: return CopyFields("", "", "", responseData)

    val requestHttp = Http.create(requestPacket.decodedData)
    if (charsetUtil.isAuto) {
      charsetUtil.setGuessedCharSet(requestHttp.body)
    }

    val method = requestHttp.method ?: ""
    val url = requestHttp.getURL(requestPacket.serverPort, requestPacket.useSSL) ?: ""
    val postData = toBodyString(requestHttp.body, charsetUtil).take(MAX_BODY_LENGTH)

    return CopyFields(method = method, url = url, postData = postData, responseData = responseData)
  }

  private fun quoteForTsv(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return "\"$escaped\""
  }

  private fun toMimeLabel(contentType: String): String? {
    val lower = contentType.lowercase()
    return when {
      "text/css" in lower -> "CSS"
      "text/html" in lower -> "HTML"
      "image/jpeg" in lower -> "JPEG"
      "image/png" in lower -> "PNG"
      "image/" in lower -> "IMAGE"
      else -> null
    }
  }

  private fun findFirstRequest(groupPackets: List<Packet>): Packet? =
    groupPackets.firstOrNull { it.direction == Packet.Direction.CLIENT }

  private fun findFirstResponse(groupPackets: List<Packet>): Packet? =
    groupPackets.firstOrNull { it.direction == Packet.Direction.SERVER }

  private fun createResponseData(responseHttp: Http, charsetUtil: CharSetUtility): String {
    val statusCode = responseHttp.statusCode ?: return ""
    val code = statusCode.toIntOrNull() ?: 0

    if (code in 300 until 400) {
      return statusCode
    }

    val contentType = responseHttp.header.getValue("Content-Type").orElse("")
    val mimeLabel = toMimeLabel(contentType)
    if (mimeLabel != null) {
      return mimeLabel
    }

    if (charsetUtil.isAuto) {
      charsetUtil.setGuessedCharSet(responseHttp.body)
    }

    return toBodyString(responseHttp.body, charsetUtil).take(MAX_BODY_LENGTH)
  }

  private fun toBodyString(body: ByteArray, charsetUtil: CharSetUtility): String {
    val charset =
      try {
        Charset.forName(charsetUtil.charSet)
      } catch (_: Exception) {
        Charset.defaultCharset()
      }
    return String(body, charset)
  }
}
