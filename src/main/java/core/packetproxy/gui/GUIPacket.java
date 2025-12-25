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
		if (showing_packet != null && showing_packet.getId() == packet.getId()) {

			return;
		} else {

			showing_packet = packet;
			// マージされた行の場合、レスポンスパケットも取得
			try {
				PacketPairingService pairingService = PacketPairingService.getInstance();
				int responsePacketId = pairingService.getResponsePacketIdForRequest(packet.getId());
				if (responsePacketId != -1) {
					showing_response_packet = Packets.getInstance().query(responsePacketId);
				} else {
					// リクエストパケットがレスポンスパケットの場合、またはマージされていない場合
					if (packet.getDirection() == Packet.Direction.SERVER) {
						showing_response_packet = packet;
						showing_packet = null;
					} else {
						showing_response_packet = null;
					}
				}
			} catch (Exception e) {
				errWithStackTrace(e);
				showing_response_packet = null;
			}
		}
		update();
	}

	public Packet getPacket() {
		return showing_packet;
	}

	public Packet getResponsePacket() {
		return showing_response_packet;
	}
}
