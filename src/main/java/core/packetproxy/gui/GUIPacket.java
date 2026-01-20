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
package packetproxy.gui;

import static packetproxy.util.Logging.errWithStackTrace;

import javax.swing.JComponent;
import javax.swing.JFrame;
import packetproxy.model.Packet;
import packetproxy.model.Packets;

public class GUIPacket {

	private static GUIPacket instance;
	private JFrame owner;
	private GUIRequestResponsePanel request_response_panel;
	private Packet showing_packet;
	private Packet showing_response_packet;

	public static GUIPacket getInstance() throws Exception {
		if (instance == null) {

			instance = new GUIPacket();
		}
		return instance;
	}

	private GUIPacket() throws Exception {
		this.owner = GUIHistory.getOwner();
		this.showing_packet = null;
		this.showing_response_packet = null;
	}

	public JComponent createPanel() throws Exception {
		request_response_panel = new GUIRequestResponsePanel(this.owner);
		return request_response_panel.createPanel();
	}

	public byte[] getData() {
		return request_response_panel.getRequestData();
	}

	public void update() {
		if (showing_packet == null && showing_response_packet == null) {

			return;
		}
		request_response_panel.setRequestPacket(showing_packet);
		request_response_panel.setResponsePacket(showing_response_packet);
	}

	public void setPacket(Packet packet) {
		setPacket(packet, false);
	}

	/**
	 * パケットを設定して表示を更新する
	 *
	 * @param packet
	 *            表示するパケット
	 * @param forceRefresh
	 *            trueの場合、同じパケットIDでも強制的に再描画する
	 */
	public void setPacket(Packet packet, boolean forceRefresh) {
		if (!forceRefresh && showing_packet != null && showing_packet.getId() == packet.getId()) {

			return;
		}

		// パケットのペアリング状態から表示モードを判断
		PacketPairingService pairingService = PacketPairingService.getInstance();
		int responsePacketId = pairingService.getResponsePacketIdForRequest(packet.getId());

		if (responsePacketId != -1) {
			// マージされている → リクエスト/レスポンス分割表示
			showing_packet = packet;
			try {
				showing_response_packet = Packets.getInstance().query(responsePacketId);
			} catch (Exception e) {
				errWithStackTrace(e);
				showing_response_packet = null;
			}
			request_response_panel.setPackets(showing_packet, showing_response_packet);
		} else if (pairingService.containsResponsePairing(packet.getId())) {
			// このパケット自体がレスポンスとしてマージされている → リクエストを取得して分割表示
			int requestPacketId = pairingService.getRequestIdForResponse(packet.getId());
			try {
				showing_packet = Packets.getInstance().query(requestPacketId);
				showing_response_packet = packet;
			} catch (Exception e) {
				errWithStackTrace(e);
				showing_packet = null;
				showing_response_packet = packet;
			}
			request_response_panel.setPackets(showing_packet, showing_response_packet);
		} else {
			// マージされていない → 単一パケット表示
			showing_packet = packet;
			showing_response_packet = null;
			request_response_panel.setSinglePacket(packet);
		}
	}

	public Packet getPacket() {
		return showing_packet;
	}

	public Packet getResponsePacket() {
		return showing_response_packet;
	}
}
