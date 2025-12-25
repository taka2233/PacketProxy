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
import java.awt.CardLayout;
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
 * HTTP以外の通信では単一パケット表示モードに切り替わる
 */
public class GUIRequestResponsePanel {

	private static final String SPLIT_VIEW = "SPLIT_VIEW";
	private static final String SINGLE_VIEW = "SINGLE_VIEW";

	private JPanel main_panel;
	private CardLayout cardLayout;
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

	// 単一パケット表示用
	private JPanel single_packet_panel;
	private JTabbedPane single_tabs;
	private TabSet single_decoded_tabs;
	private GUIData single_received_panel;
	private GUIData single_modified_panel;
	private GUIData single_sent_panel;
	private JComponent single_all_panel;
	private RawTextPane single_all_received;
	private RawTextPane single_all_decoded;
	private RawTextPane single_all_modified;
	private RawTextPane single_all_sent;

	// 現在表示中のパケット
	private Packet showing_request_packet;
	private Packet showing_response_packet;
	private Packet showing_single_packet;
	private String currentView = SPLIT_VIEW;

	private javax.swing.JFrame owner;

	public GUIRequestResponsePanel(javax.swing.JFrame owner) {
		this.owner = owner;
	}

	public JComponent createPanel() throws Exception {
		cardLayout = new CardLayout();
		main_panel = new JPanel(cardLayout);

		// === 分割ビュー（HTTP用）===
		// リクエストパネルの作成
		request_panel = createRequestPanel();

		// レスポンスパネルの作成
		response_panel = createResponsePanel();

		// 左右に分割
		split_pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, request_panel, response_panel);
		split_pane.setResizeWeight(0.5);
		split_pane.setContinuousLayout(true);
		split_pane.setDividerSize(8);

		main_panel.add(split_pane, SPLIT_VIEW);

		// === 単一パケットビュー（非HTTP用）===
		single_packet_panel = createSinglePacketPanel();
		main_panel.add(single_packet_panel, SINGLE_VIEW);

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

	private JPanel createSinglePacketPanel() throws Exception {
		JPanel panel = new JPanel();
		panel.setLayout(new BorderLayout());
		panel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(new Color(0x66, 0x66, 0x99), 2),
				"Streaming Packet",
				TitledBorder.LEFT,
				TitledBorder.TOP,
				null,
				new Color(0x66, 0x66, 0x99)));

		// タブの作成
		single_tabs = new JTabbedPane();

		// Decoded タブ（メインのタブ）
		single_decoded_tabs = new TabSet(true, false);
		single_tabs.addTab("Decoded", single_decoded_tabs.getTabPanel());

		// Received Packet タブ
		single_received_panel = new GUIData(owner);
		single_tabs.addTab("Received Packet", single_received_panel.createPanel());

		// Modified タブ
		single_modified_panel = new GUIData(owner);
		single_tabs.addTab("Modified", single_modified_panel.createPanel());

		// Encoded (Sent Packet) タブ
		single_sent_panel = new GUIData(owner);
		single_tabs.addTab("Encoded (Sent Packet)", single_sent_panel.createPanel());

		// All タブ
		single_all_panel = createAllPanelForSingle();
		single_tabs.addTab("All", single_all_panel);

		// タブ変更リスナー
		single_tabs.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				updateSinglePacketPanel();
			}
		});

		panel.add(single_tabs, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createAllPanelForSingle() throws Exception {
		JPanel panel = new JPanel();
		panel.setLayout(new GridLayout(1, 4));

		single_all_received = createTextPaneForAll(panel, I18nString.get("Received"));
		single_all_decoded = createTextPaneForAll(panel, I18nString.get("Decoded"));
		single_all_modified = createTextPaneForAll(panel, I18nString.get("Modified"));
		single_all_sent = createTextPaneForAll(panel, I18nString.get("Encoded"));

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

	/**
	 * 単一パケット表示モード用：パケットを設定
	 * Streaming通信で使用
	 */
	public void setSinglePacket(Packet packet) {
		showing_single_packet = packet;
		switchToSingleView();
		updateSinglePacketPanel();
	}

	/**
	 * リクエスト/レスポンス分割表示モード用：両方のパケットを設定
	 * HTTP通信で使用
	 */
	public void setPackets(Packet requestPacket, Packet responsePacket) {
		showing_request_packet = requestPacket;
		showing_response_packet = responsePacket;
		switchToSplitView();
		updateRequestPanel();
		updateResponsePanel();
	}

	private void switchToSplitView() {
		if (!SPLIT_VIEW.equals(currentView)) {
			currentView = SPLIT_VIEW;
			cardLayout.show(main_panel, SPLIT_VIEW);
		}
	}

	private void switchToSingleView() {
		if (!SINGLE_VIEW.equals(currentView)) {
			currentView = SINGLE_VIEW;
			cardLayout.show(main_panel, SINGLE_VIEW);
		}
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

	private void updateSinglePacketPanel() {
		if (showing_single_packet == null) {
			clearSinglePacketPanel();
			return;
		}
		try {
			int selectedIndex = single_tabs.getSelectedIndex();
			switch (selectedIndex) {
				case 0: // Decoded
					byte[] decodedData = showing_single_packet.getDecodedData();
					if (decodedData == null || decodedData.length == 0) {
						decodedData = showing_single_packet.getModifiedData();
					}
					if (decodedData == null) {
						decodedData = new byte[]{};
					}
					single_decoded_tabs.setData(decodedData);
					break;
				case 1: // Received Packet
					single_received_panel.setData(showing_single_packet.getReceivedData());
					break;
				case 2: // Modified
					single_modified_panel.setData(showing_single_packet.getModifiedData());
					break;
				case 3: // Encoded (Sent Packet)
					single_sent_panel.setData(showing_single_packet.getSentData());
					break;
				case 4: // All
					single_all_received.setData(showing_single_packet.getReceivedData(), true);
					single_all_received.setCaretPosition(0);
					single_all_decoded.setData(showing_single_packet.getDecodedData(), true);
					single_all_decoded.setCaretPosition(0);
					single_all_modified.setData(showing_single_packet.getModifiedData(), true);
					single_all_modified.setCaretPosition(0);
					single_all_sent.setData(showing_single_packet.getSentData(), true);
					single_all_sent.setCaretPosition(0);
					break;
			}
		} catch (Exception e) {
			errWithStackTrace(e);
		}
	}

	private void clearSinglePacketPanel() {
		try {
			single_decoded_tabs.setData(new byte[]{});
			single_received_panel.setData(new byte[]{});
			single_modified_panel.setData(new byte[]{});
			single_sent_panel.setData(new byte[]{});
			single_all_received.setData(new byte[]{}, true);
			single_all_decoded.setData(new byte[]{}, true);
			single_all_modified.setData(new byte[]{}, true);
			single_all_sent.setData(new byte[]{}, true);
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
