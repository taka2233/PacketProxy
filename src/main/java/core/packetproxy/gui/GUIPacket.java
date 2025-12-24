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
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import packetproxy.model.Packet;
import packetproxy.model.Packets;

public class GUIPacket {

	private static GUIPacket instance;
	private JFrame owner;
	private GUIDataAll all_panel;
	private JTabbedPane packet_pane;
	private GUIData received_panel;
	private GUIData decoded_panel;
	private GUIData modified_panel;
	private GUIData sent_panel;
	private GUIRequestResponsePanel request_response_panel;
	private Packet showing_packet;
	private Packet showing_response_packet;

	// public static void main(String args[])
	// {
	// try {
	// GUIPacket gui = new GUIPacket();
	// String s = "ABgNBHJfb2sAAAJhbANtc2cAB4NoAmEMYQANCg0KeyJlbXB0eSI6N30=";
	// byte[] data = Base64.getDecoder().decode(s.getBytes());
	// byte[] result = gui.prettyFormatJSONInRawData(data, "hoge");
	// System.out.println(new String(result));
	// } catch (Exception e) {
	// errWithStackTrace(e);
	// }
	// }

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
		received_panel = new GUIData(this.owner);
		decoded_panel = new GUIData(this.owner);
		modified_panel = new GUIData(this.owner);
		sent_panel = new GUIData(this.owner);
		all_panel = new GUIDataAll();
		request_response_panel = new GUIRequestResponsePanel(this.owner);

		packet_pane = new JTabbedPane();
		packet_pane.addTab("Request / Response", request_response_panel.createPanel());
		packet_pane.addTab("Received Packet", received_panel.createPanel());
		packet_pane.addTab("Decoded", decoded_panel.createPanel());
		packet_pane.addTab("Modified", modified_panel.createPanel());
		packet_pane.addTab("Encoded (Sent Packet)", sent_panel.createPanel());
		packet_pane.addTab("All", all_panel.createPanel());
		packet_pane.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent e) {
				try {

					update();
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		});
		packet_pane.setSelectedIndex(0); /* Request / Response */
		return packet_pane;
	}

	public byte[] getData() {
		switch (packet_pane.getSelectedIndex()) {
			case 0 :
				return request_response_panel.getRequestData();
			case 1 :
				return received_panel.getData();
			case 2 :
				return decoded_panel.getData();
			case 3 :
				return modified_panel.getData();
			case 4 :
				return sent_panel.getData();
			default :
				return modified_panel.getData();
		}
	}

	public void update() {
		if (showing_packet == null) {

			return;
		}
		switch (packet_pane.getSelectedIndex()) {
			case 0 :
				request_response_panel.setRequestPacket(showing_packet);
				request_response_panel.setResponsePacket(showing_response_packet);
				break;
			case 1 :
				received_panel.setData(showing_packet.getReceivedData());
				break;
			case 2 :
				decoded_panel.setData(showing_packet.getDecodedData());
				break;
			case 3 :
				modified_panel.setData(showing_packet.getModifiedData());
				break;
			case 4 :
				sent_panel.setData(showing_packet.getSentData());
				break;
			case 5 :
				all_panel.setPacket(showing_packet);
				break;
			default :
		}
	}

	public void setPacket(Packet packet) {
		if (showing_packet != null && showing_packet.getId() == packet.getId()) {

			return;
		} else {

			showing_packet = packet;
			// マージされた行の場合、レスポンスパケットも取得
			try {
				GUIHistory history = GUIHistory.getInstance();
				int responsePacketId = history.getResponsePacketIdForRequest(packet.getId());
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
