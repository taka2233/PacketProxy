package packetproxy.gui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PacketPairingServiceTest {
  @Test
  fun registerPairing_registersBidirectionalMappings() {
    val service = PacketPairingService()

    val requestPacketId = 100
    val responsePacketId = 200
    service.registerPairing(responsePacketId, requestPacketId)

    assertThat(service.getRequestIdForResponse(responsePacketId)).isEqualTo(requestPacketId)
    assertThat(service.getResponsePacketIdForRequest(requestPacketId)).isEqualTo(responsePacketId)
    assertThat(service.containsResponsePairing(responsePacketId)).isTrue()
    assertThat(service.isMergedRow(requestPacketId)).isTrue()
    assertThat(service.isMergedRow(responsePacketId)).isFalse()
  }

  @Test
  fun unregisterPairingByRequestId_removesMappingsAndReturnsResponseId() {
    val service = PacketPairingService()

    val requestPacketId = 101
    val responsePacketId = 201
    service.registerPairing(responsePacketId, requestPacketId)

    val removedResponsePacketId = service.unregisterPairingByRequestId(requestPacketId)

    assertThat(removedResponsePacketId).isEqualTo(responsePacketId)
    assertThat(service.containsResponsePairing(responsePacketId)).isFalse()
    assertThat(service.getRequestIdForResponse(responsePacketId)).isEqualTo(-1)
    assertThat(service.getResponsePacketIdForRequest(requestPacketId)).isEqualTo(-1)
    assertThat(service.isMergedRow(requestPacketId)).isFalse()
  }

  @Test
  fun groupPacketCount_andMergeableBoundary() {
    val service = PacketPairingService()
    val groupId = 1L

    assertThat(service.incrementGroupPacketCount(groupId)).isEqualTo(1)
    assertThat(service.isGroupMergeable(groupId)).isTrue()

    assertThat(service.incrementGroupPacketCount(groupId)).isEqualTo(2)
    assertThat(service.isGroupMergeable(groupId)).isTrue()

    assertThat(service.incrementGroupPacketCount(groupId)).isEqualTo(3)
    assertThat(service.getGroupPacketCount(groupId)).isEqualTo(3)
    assertThat(service.isGroupMergeable(groupId)).isFalse()
  }

  @Test
  fun twoClientPacketsInSameGroup_notMergeable() {
    val service = PacketPairingService()
    val groupId = 9L

    // 1つ目のCLIENTパケット：まだマージ可能
    service.incrementGroupPacketCount(groupId)
    service.incrementGroupClientPacketCount(groupId)
    assertThat(service.isGroupMergeable(groupId)).isTrue()

    // 2つ目のCLIENTパケット：同一グループに2つのRequestが存在 → ストリーミング扱い、マージ不可
    service.incrementGroupPacketCount(groupId)
    service.incrementGroupClientPacketCount(groupId)
    assertThat(service.getGroupPacketCount(groupId)).isEqualTo(2)
    assertThat(service.getGroupClientPacketCount(groupId)).isEqualTo(2)
    assertThat(service.isGroupMergeable(groupId)).isFalse()
    assertThat(service.isGroupStreaming(groupId)).isTrue()
  }

  @Test
  fun groupClientPacketCount_andStreamingBoundary() {
    val service = PacketPairingService()
    val groupId = 2L

    assertThat(service.incrementGroupClientPacketCount(groupId)).isEqualTo(1)
    assertThat(service.isGroupStreaming(groupId)).isFalse()

    assertThat(service.incrementGroupClientPacketCount(groupId)).isEqualTo(2)
    assertThat(service.getGroupClientPacketCount(groupId)).isEqualTo(2)
    assertThat(service.isGroupStreaming(groupId)).isTrue()
  }

  @Test
  fun clear_resetsAllState() {
    val service = PacketPairingService()

    service.registerGroupRow(10L, 3)
    service.markGroupHasResponse(10L)
    service.registerPairing(210, 110)
    service.incrementGroupPacketCount(10L)
    service.incrementGroupClientPacketCount(10L)

    service.clear()

    assertThat(service.getRowForGroup(10L)).isNull()
    assertThat(service.hasResponse(10L)).isFalse()
    assertThat(service.containsResponsePairing(210)).isFalse()
    assertThat(service.getGroupPacketCount(10L)).isEqualTo(0)
    assertThat(service.getGroupClientPacketCount(10L)).isEqualTo(0)
    assertThat(service.isGroupStreaming(10L)).isFalse()
  }

  @Test
  fun unmergeGroup_onlyClearsHasResponse() {
    val service = PacketPairingService()
    val groupId = 5L

    service.markGroupHasResponse(groupId)
    service.registerPairing(responsePacketId = 301, requestPacketId = 201)

    service.unmergeGroup(groupId)

    assertThat(service.hasResponse(groupId)).isFalse()
    // Pairing is maintained until explicitly unregistered.
    assertThat(service.getResponsePacketIdForRequest(201)).isEqualTo(301)
    assertThat(service.getRequestIdForResponse(301)).isEqualTo(201)
  }
}
