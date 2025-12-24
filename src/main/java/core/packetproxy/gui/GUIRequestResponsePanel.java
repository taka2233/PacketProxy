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
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.border.TitledBorder;
import packetproxy.model.Packet;

/**
 * リクエストとレスポンスを左右に並べて表示するパネル
 */
public class GUIRequestResponsePanel {

	private JPanel main_panel;
	private TabSet request_tabs;
	private TabSet response_tabs;
	private JPanel request_panel;
	private JPanel response_panel;
	private JSplitPane split_pane;

	public GUIRequestResponsePanel(@SuppressWarnings("unused") javax.swing.JFrame owner) {
		// ownerは将来の拡張用に受け取るが、現在は使用しない
	}

	public JComponent createPanel() throws Exception {
		main_panel = new JPanel();
		main_panel.setLayout(new BorderLayout());

		// リクエストパネル
		request_panel = new JPanel();
		request_panel.setLayout(new BoxLayout(request_panel, BoxLayout.Y_AXIS));
		request_panel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(new Color(0x33, 0x99, 0xff), 2),
				"Request",
				TitledBorder.LEFT,
				TitledBorder.TOP,
				null,
				new Color(0x33, 0x99, 0xff)));
		request_tabs = new TabSet(true, false);
		request_panel.add(request_tabs.getTabPanel());

		// レスポンスパネル
		response_panel = new JPanel();
		response_panel.setLayout(new BoxLayout(response_panel, BoxLayout.Y_AXIS));
		response_panel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(new Color(0x99, 0x33, 0x33), 2),
				"Response",
				TitledBorder.LEFT,
				TitledBorder.TOP,
				null,
				new Color(0x99, 0x33, 0x33)));
		response_tabs = new TabSet(true, false);
		response_panel.add(response_tabs.getTabPanel());

		// 左右に分割
		split_pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, request_panel, response_panel);
		split_pane.setResizeWeight(0.5);
		split_pane.setOneTouchExpandable(true);

		main_panel.add(split_pane, BorderLayout.CENTER);
		return main_panel;
	}

	public void setRequestPacket(Packet packet) {
		if (packet == null) {
			request_tabs.setData(new byte[]{});
			return;
		}
		try {
			byte[] data = packet.getDecodedData();
			if (data == null || data.length == 0) {
				data = packet.getModifiedData();
			}
			if (data == null) {
				data = new byte[]{};
			}
			request_tabs.setData(data);
		} catch (Exception e) {
			errWithStackTrace(e);
		}
	}

	public void setResponsePacket(Packet packet) {
		if (packet == null) {
			response_tabs.setData(new byte[]{});
			return;
		}
		try {
			byte[] data = packet.getDecodedData();
			if (data == null || data.length == 0) {
				data = packet.getModifiedData();
			}
			if (data == null) {
				data = new byte[]{};
			}
			response_tabs.setData(data);
		} catch (Exception e) {
			errWithStackTrace(e);
		}
	}

	public byte[] getRequestData() {
		return request_tabs.getData();
	}

	public byte[] getResponseData() {
		return response_tabs.getData();
	}
}

