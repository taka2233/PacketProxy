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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import packetproxy.common.I18nString;
import packetproxy.model.Packet;

/**
 * リクエストとレスポンスを左右に並べて表示するパネル
 * 各パネルにReceived Packet, Decoded, Modified, Encoded, Allのタブを持つ
 */
public class GUIRequestResponsePanel {

	private JPanel main_panel;
	private JSplitPane split_pane;

	// Request側
	private JPanel request_panel;
	private JTabbedPane request_tabs;
	private TabSet request_decoded_tabs;
	private GUIData request_received_panel;
	private GUIData request_modified_panel;
	private GUIData request_sent_panel;
	private JComponent request_all_panel;
	private RawTextPane request_all_received;
	private RawTextPane request_all_decoded;
	private RawTextPane request_all_modified;
	private RawTextPane request_all_sent;

	// Response側
	private JPanel response_panel;
	private JTabbedPane response_tabs;
	private TabSet response_decoded_tabs;
	private GUIData response_received_panel;
	private GUIData response_modified_panel;
	private GUIData response_sent_panel;
	private JComponent response_all_panel;
	private RawTextPane response_all_received;
	private RawTextPane response_all_decoded;
	private RawTextPane response_all_modified;
	private RawTextPane response_all_sent;

	// 現在表示中のパケット
	private Packet showing_request_packet;
	private Packet showing_response_packet;

	private javax.swing.JFrame owner;

	public GUIRequestResponsePanel(javax.swing.JFrame owner) {
		this.owner = owner;
	}

	public JComponent createPanel() throws Exception {
		main_panel = new JPanel();
		main_panel.setLayout(new BorderLayout());

		// リクエストパネルの作成
		request_panel = createRequestPanel();

		// レスポンスパネルの作成
		response_panel = createResponsePanel();

		// 左右に分割
		split_pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, request_panel, response_panel);
		split_pane.setResizeWeight(0.5);
		split_pane.setOneTouchExpandable(true);
		split_pane.setContinuousLayout(true);
		split_pane.setDividerSize(8);

		main_panel.add(split_pane, BorderLayout.CENTER);
		return main_panel;
	}

	private JPanel createRequestPanel() throws Exception {
		JPanel panel = new JPanel();
		panel.setLayout(new BorderLayout());
		panel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(new Color(0x33, 0x99, 0xff), 2),
				"Request",
				TitledBorder.LEFT,
				TitledBorder.TOP,
				null,
				new Color(0x33, 0x99, 0xff)));
		// JSplitPaneでリサイズできるように最小サイズを設定
		panel.setMinimumSize(new Dimension(100, 100));

		// タブの作成
		request_tabs = new JTabbedPane();

		// Decoded タブ（メインのタブ）
		request_decoded_tabs = new TabSet(true, false);
		request_tabs.addTab("Decoded", request_decoded_tabs.getTabPanel());

		// Received Packet タブ
		request_received_panel = new GUIData(owner);
		request_tabs.addTab("Received Packet", request_received_panel.createPanel());

		// Modified タブ
		request_modified_panel = new GUIData(owner);
		request_tabs.addTab("Modified", request_modified_panel.createPanel());

		// Encoded (Sent Packet) タブ
		request_sent_panel = new GUIData(owner);
		request_tabs.addTab("Encoded (Sent Packet)", request_sent_panel.createPanel());

		// All タブ
		request_all_panel = createAllPanel(true);
		request_tabs.addTab("All", request_all_panel);

		// タブ変更リスナー
		request_tabs.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				updateRequestPanel();
			}
		});

		panel.add(request_tabs, BorderLayout.CENTER);
		return panel;
	}

	private JPanel createResponsePanel() throws Exception {
		JPanel panel = new JPanel();
		panel.setLayout(new BorderLayout());
		panel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(new Color(0x99, 0x33, 0x33), 2),
				"Response",
				TitledBorder.LEFT,
				TitledBorder.TOP,
				null,
				new Color(0x99, 0x33, 0x33)));
		// JSplitPaneでリサイズできるように最小サイズを設定
		panel.setMinimumSize(new Dimension(100, 100));

		// タブの作成
		response_tabs = new JTabbedPane();

		// Decoded タブ（メインのタブ）
		response_decoded_tabs = new TabSet(true, false);
		response_tabs.addTab("Decoded", response_decoded_tabs.getTabPanel());

		// Received Packet タブ
		response_received_panel = new GUIData(owner);
		response_tabs.addTab("Received Packet", response_received_panel.createPanel());

		// Modified タブ
		response_modified_panel = new GUIData(owner);
		response_tabs.addTab("Modified", response_modified_panel.createPanel());

		// Encoded (Sent Packet) タブ
		response_sent_panel = new GUIData(owner);
		response_tabs.addTab("Encoded (Sent Packet)", response_sent_panel.createPanel());

		// All タブ
		response_all_panel = createAllPanel(false);
		response_tabs.addTab("All", response_all_panel);

		// タブ変更リスナー
		response_tabs.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				updateResponsePanel();
			}
		});

		panel.add(response_tabs, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createAllPanel(boolean isRequest) throws Exception {
		JPanel panel = new JPanel();
		panel.setLayout(new GridLayout(1, 4));

		RawTextPane received = createTextPaneForAll(panel, I18nString.get("Received"));
		RawTextPane decoded = createTextPaneForAll(panel, I18nString.get("Decoded"));
		RawTextPane modified = createTextPaneForAll(panel, I18nString.get("Modified"));
		RawTextPane sent = createTextPaneForAll(panel, I18nString.get("Encoded"));

		if (isRequest) {
			request_all_received = received;
			request_all_decoded = decoded;
			request_all_modified = modified;
			request_all_sent = sent;
		} else {
			response_all_received = received;
			response_all_decoded = decoded;
			response_all_modified = modified;
			response_all_sent = sent;
		}

		return panel;
	}

	private RawTextPane createTextPaneForAll(JPanel parentPanel, String labelName) throws Exception {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		JLabel label = new JLabel(labelName);
		label.setAlignmentX(0.5f);

		RawTextPane text = new RawTextPane();
		text.setEditable(false);
		panel.add(label);
		JScrollPane scroll = new JScrollPane(text);
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		panel.add(scroll);
		parentPanel.add(panel);
		return text;
	}

	public void setRequestPacket(Packet packet) {
		showing_request_packet = packet;
		updateRequestPanel();
	}

	public void setResponsePacket(Packet packet) {
		showing_response_packet = packet;
		updateResponsePanel();
	}

	private void updateRequestPanel() {
		if (showing_request_packet == null) {
			clearRequestPanel();
			return;
		}
		try {
			int selectedIndex = request_tabs.getSelectedIndex();
			switch (selectedIndex) {
				case 0: // Decoded
					byte[] decodedData = showing_request_packet.getDecodedData();
					if (decodedData == null || decodedData.length == 0) {
						decodedData = showing_request_packet.getModifiedData();
					}
					if (decodedData == null) {
						decodedData = new byte[]{};
					}
					request_decoded_tabs.setData(decodedData);
					break;
				case 1: // Received Packet
					request_received_panel.setData(showing_request_packet.getReceivedData());
					break;
				case 2: // Modified
					request_modified_panel.setData(showing_request_packet.getModifiedData());
					break;
				case 3: // Encoded (Sent Packet)
					request_sent_panel.setData(showing_request_packet.getSentData());
					break;
				case 4: // All
					request_all_received.setData(showing_request_packet.getReceivedData(), true);
					request_all_received.setCaretPosition(0);
					request_all_decoded.setData(showing_request_packet.getDecodedData(), true);
					request_all_decoded.setCaretPosition(0);
					request_all_modified.setData(showing_request_packet.getModifiedData(), true);
					request_all_modified.setCaretPosition(0);
					request_all_sent.setData(showing_request_packet.getSentData(), true);
					request_all_sent.setCaretPosition(0);
					break;
			}
		} catch (Exception e) {
			errWithStackTrace(e);
		}
	}

	private void updateResponsePanel() {
		if (showing_response_packet == null) {
			clearResponsePanel();
			return;
		}
		try {
			int selectedIndex = response_tabs.getSelectedIndex();
			switch (selectedIndex) {
				case 0: // Decoded
					byte[] decodedData = showing_response_packet.getDecodedData();
					if (decodedData == null || decodedData.length == 0) {
						decodedData = showing_response_packet.getModifiedData();
					}
					if (decodedData == null) {
						decodedData = new byte[]{};
					}
					response_decoded_tabs.setData(decodedData);
					break;
				case 1: // Received Packet
					response_received_panel.setData(showing_response_packet.getReceivedData());
					break;
				case 2: // Modified
					response_modified_panel.setData(showing_response_packet.getModifiedData());
					break;
				case 3: // Encoded (Sent Packet)
					response_sent_panel.setData(showing_response_packet.getSentData());
					break;
				case 4: // All
					response_all_received.setData(showing_response_packet.getReceivedData(), true);
					response_all_received.setCaretPosition(0);
					response_all_decoded.setData(showing_response_packet.getDecodedData(), true);
					response_all_decoded.setCaretPosition(0);
					response_all_modified.setData(showing_response_packet.getModifiedData(), true);
					response_all_modified.setCaretPosition(0);
					response_all_sent.setData(showing_response_packet.getSentData(), true);
					response_all_sent.setCaretPosition(0);
					break;
			}
		} catch (Exception e) {
			errWithStackTrace(e);
		}
	}

	private void clearRequestPanel() {
		try {
			request_decoded_tabs.setData(new byte[]{});
			request_received_panel.setData(new byte[]{});
			request_modified_panel.setData(new byte[]{});
			request_sent_panel.setData(new byte[]{});
			request_all_received.setData(new byte[]{}, true);
			request_all_decoded.setData(new byte[]{}, true);
			request_all_modified.setData(new byte[]{}, true);
			request_all_sent.setData(new byte[]{}, true);
		} catch (Exception e) {
			errWithStackTrace(e);
		}
	}

	private void clearResponsePanel() {
		try {
			response_decoded_tabs.setData(new byte[]{});
			response_received_panel.setData(new byte[]{});
			response_modified_panel.setData(new byte[]{});
			response_sent_panel.setData(new byte[]{});
			response_all_received.setData(new byte[]{}, true);
			response_all_decoded.setData(new byte[]{}, true);
			response_all_modified.setData(new byte[]{}, true);
			response_all_sent.setData(new byte[]{}, true);
		} catch (Exception e) {
			errWithStackTrace(e);
		}
	}

	public byte[] getRequestData() {
		if (showing_request_packet == null) {
			return new byte[]{};
		}
		// Decodedタブからデータを取得
		return request_decoded_tabs.getData();
	}

	public byte[] getResponseData() {
		if (showing_response_packet == null) {
			return new byte[]{};
		}
		// Decodedタブからデータを取得
		return response_decoded_tabs.getData();
	}
}
