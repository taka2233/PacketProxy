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
package packetproxy.extensions.securityheaders;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import packetproxy.extensions.securityheaders.checks.*;
import packetproxy.http.Http;
import packetproxy.http.HttpHeader;
import packetproxy.model.Extension;
import packetproxy.model.Packet;
import packetproxy.model.Packets;

/**
 * Security Headers Extension for PacketProxy. Analyzes HTTP responses for
 * security header compliance.
 *
 * <p>
 * To add a new security check: 1. Create a new class implementing SecurityCheck
 * interface 2. Add the check to the SECURITY_CHECKS list in this class
 */
public class SecurityHeadersExtension extends Extension {

	// ===== Registered Security Checks =====
	// Add new checks here to extend functionality
	private static final List<SecurityCheck> SECURITY_CHECKS = Arrays.asList(new CspCheck(), new XssProtectionCheck(),
			new HstsCheck(), new ContentTypeCheck(), new CacheControlCheck(), new CookieCheck(), new CorsCheck());

	private JTable table;
	private DefaultTableModel model;
	private Map<String, Integer> endpointMap;
	private Map<String, Packet> packetMap;
	private Map<String, Map<String, SecurityCheckResult>> resultsMap;
	private JTextPane detailArea;
	private JTextPane headerPane;

	public SecurityHeadersExtension() {
		super();
		this.setName("SecurityHeaders");
		this.endpointMap = new HashMap<>();
		this.packetMap = new HashMap<>();
		this.resultsMap = new HashMap<>();
	}

	@Override
	public JComponent createPanel() throws Exception {
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(createButtonPanel(), BorderLayout.NORTH);

		initializeTableModel();
		initializeTable();

		JScrollPane tableScrollPane = new JScrollPane(table);
		JSplitPane bottomSplit = createDetailPanes();

		JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScrollPane, bottomSplit);
		mainSplit.setDividerLocation(300);
		panel.add(mainSplit, BorderLayout.CENTER);

		setupSelectionListener();

		return panel;
	}

	// ===== UI Component Creation =====

	private JPanel createButtonPanel() {
		JPanel buttonPanel = new JPanel();

		JButton scanButton = new JButton("Scan History");
		scanButton.addActionListener(e -> scanHistory());
		buttonPanel.add(scanButton);

		JButton clearButton = new JButton("Clear");
		clearButton.addActionListener(e -> clearTable());
		buttonPanel.add(clearButton);

		return buttonPanel;
	}

	private void initializeTableModel() {
		// Build columns dynamically from registered checks
		List<String> columns = new ArrayList<>();
		columns.add("Method");
		columns.add("URL");
		columns.add("Code");
		for (SecurityCheck check : SECURITY_CHECKS) {
			columns.add(check.getColumnName());
		}
		columns.add("Status");

		model = new DefaultTableModel(columns.toArray(new String[0]), 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
	}

	private void initializeTable() {
		table = new JTable(model);
		table.setDefaultRenderer(Object.class, new SecurityHeaderRenderer());
		table.setAutoCreateRowSorter(true);

		// Hide Status column from view but keep in model for renderer
		table.removeColumn(table.getColumnModel().getColumn(table.getColumnModel().getColumnIndex("Status")));
	}

	private JSplitPane createDetailPanes() {
		headerPane = new JTextPane();
		headerPane.setEditable(false);
		headerPane.setBackground(Color.WHITE);
		JScrollPane headerScrollPane = new JScrollPane(headerPane);

		detailArea = new JTextPane();
		detailArea.setEditable(false);
		detailArea.setBackground(Color.WHITE);
		JScrollPane detailScrollPane = new JScrollPane(detailArea);

		JSplitPane bottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, headerScrollPane, detailScrollPane);
		bottomSplit.setResizeWeight(0.5);

		return bottomSplit;
	}

	// ===== Text Styles =====

	private static class TextStyles {
		final SimpleAttributeSet green;
		final SimpleAttributeSet red;
		final SimpleAttributeSet yellow;
		final SimpleAttributeSet black;
		final SimpleAttributeSet bold;

		TextStyles() {
			green = new SimpleAttributeSet();
			StyleConstants.setForeground(green, new Color(0, 128, 0));
			StyleConstants.setBackground(green, new Color(240, 255, 240));

			red = new SimpleAttributeSet();
			StyleConstants.setForeground(red, new Color(200, 0, 0));
			StyleConstants.setBold(red, true);
			StyleConstants.setBackground(red, new Color(255, 240, 240));

			yellow = new SimpleAttributeSet();
			StyleConstants.setForeground(yellow, new Color(220, 130, 0));
			StyleConstants.setBackground(yellow, new Color(255, 255, 240));

			black = new SimpleAttributeSet();
			StyleConstants.setForeground(black, Color.BLACK);

			bold = new SimpleAttributeSet();
			StyleConstants.setBold(bold, true);
			StyleConstants.setForeground(bold, Color.BLACK);
		}
	}

	// ===== Selection Listener =====

	private void setupSelectionListener() {
		table.getSelectionModel().addListSelectionListener(event -> {
			if (event.getValueIsAdjusting())
				return;

			int viewRow = table.getSelectedRow();
			if (viewRow == -1)
				return;

			int modelRow = table.convertRowIndexToModel(viewRow);
			String method = (String) model.getValueAt(modelRow, 0);
			String url = (String) model.getValueAt(modelRow, 1);
			String statusCode = (String) model.getValueAt(modelRow, 2);
			String key = method + " " + url + " " + statusCode;

			Packet p = packetMap.get(key);
			if (p == null)
				return;

			Map<String, SecurityCheckResult> results = resultsMap.get(key);
			if (results == null)
				return;

			try {
				Http http = Http.create(p.getDecodedData());
				HttpHeader header = http.getHeader();
				TextStyles styles = new TextStyles();

				populateHeaderPane(header, results, styles);
				populateIssuesPane(header, results, styles);

			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	private void populateHeaderPane(HttpHeader header, Map<String, SecurityCheckResult> results, TextStyles styles)
			throws Exception {
		StyledDocument doc = headerPane.getStyledDocument();
		headerPane.setText("");

		// Status line
		doc.insertString(doc.getLength(), header.getStatusline() + "\n", styles.bold);

		// All headers with color coding
		byte[] headerBytes = header.toByteArray();
		String rawHeaders = new String(headerBytes, "UTF-8");
		String[] lines = rawHeaders.split("\r\n|\n");

		for (String line : lines) {
			if (line.isEmpty())
				continue;
			SimpleAttributeSet style = getStyleForHeaderLine(line, results, styles);
			doc.insertString(doc.getLength(), line + "\n", style);
		}
	}

	private SimpleAttributeSet getStyleForHeaderLine(String line, Map<String, SecurityCheckResult> results,
			TextStyles styles) {
		String lowerLine = line.toLowerCase();

		for (SecurityCheck check : SECURITY_CHECKS) {
			SecurityCheckResult result = results.get(check.getName());
			SecurityCheck.HighlightType type = check.getHighlightType(line, result);
			if (type == SecurityCheck.HighlightType.GREEN) {
				return styles.green;
			} else if (type == SecurityCheck.HighlightType.RED) {
				return styles.red;
			}
		}

		// Special handling for Set-Cookie (per-line check)
		if (lowerLine.startsWith("set-cookie:")) {
			return CookieCheck.hasSecureFlag(lowerLine) ? styles.green : styles.red;
		}

		return styles.black;
	}

	private void populateIssuesPane(HttpHeader header, Map<String, SecurityCheckResult> results, TextStyles styles)
			throws Exception {
		StyledDocument doc = detailArea.getStyledDocument();
		detailArea.setText("");

		doc.insertString(doc.getLength(), "Security Check Results\n", styles.bold);
		doc.insertString(doc.getLength(), "=".repeat(40) + "\n\n", styles.black);

		// Display results for each check
		for (SecurityCheck check : SECURITY_CHECKS) {
			SecurityCheckResult result = results.get(check.getName());
			if (result != null) {
				writeCheckResult(doc, check, result, styles);
			}
		}

		// Cookie details
		populateCookieDetails(doc, header, styles);
	}

	private void writeCheckResult(StyledDocument doc, SecurityCheck check, SecurityCheckResult result,
			TextStyles styles) throws Exception {
		doc.insertString(doc.getLength(), check.getName() + ": ", styles.bold);

		if (result.isOk()) {
			doc.insertString(doc.getLength(), "OK\n", styles.green);
			doc.insertString(doc.getLength(), "  " + result.getDisplayValue() + "\n\n", styles.black);
		} else if (result.isWarn()) {
			doc.insertString(doc.getLength(), "WARNING\n", styles.yellow);
			doc.insertString(doc.getLength(), "  " + check.getMissingMessage() + "\n", styles.red);
			doc.insertString(doc.getLength(), "  Current: " + result.getDisplayValue() + "\n\n", styles.black);
		} else {
			doc.insertString(doc.getLength(), "FAIL\n", styles.red);
			doc.insertString(doc.getLength(), "  " + check.getMissingMessage() + "\n", styles.red);
			doc.insertString(doc.getLength(), "  Current: " + result.getDisplayValue() + "\n\n", styles.black);
		}
	}

	private void populateCookieDetails(StyledDocument doc, HttpHeader header, TextStyles styles) throws Exception {
		List<String> setCookies = header.getAllValue("Set-Cookie");
		if (setCookies.isEmpty())
			return;

		doc.insertString(doc.getLength(), "\n" + "=".repeat(40) + "\n", styles.black);
		doc.insertString(doc.getLength(), "Cookie Details\n", styles.bold);
		doc.insertString(doc.getLength(), "=".repeat(40) + "\n\n", styles.black);

		for (String cookie : setCookies) {
			writeCookieDetail(doc, cookie, styles);
		}
	}

	private void writeCookieDetail(StyledDocument doc, String cookie, TextStyles styles) throws Exception {
		String lowerCookie = cookie.toLowerCase();
		boolean hasSecure = lowerCookie.contains("secure");
		boolean hasHttpOnly = lowerCookie.contains("httponly");
		boolean hasSameSite = lowerCookie.contains("samesite");

		String cookieName = cookie.split("=")[0];
		doc.insertString(doc.getLength(), cookieName + ":\n", styles.bold);
		doc.insertString(doc.getLength(), "  " + cookie + "\n", styles.black);

		doc.insertString(doc.getLength(), "  Secure: ", styles.black);
		doc.insertString(doc.getLength(), hasSecure ? "Yes\n" : "Missing!\n", hasSecure ? styles.green : styles.red);

		doc.insertString(doc.getLength(), "  HttpOnly: ", styles.black);
		doc.insertString(doc.getLength(), hasHttpOnly ? "Yes\n" : "Missing!\n",
				hasHttpOnly ? styles.green : styles.red);

		doc.insertString(doc.getLength(), "  SameSite: ", styles.black);
		doc.insertString(doc.getLength(), hasSameSite ? "Yes\n" : "Missing!\n",
				hasSameSite ? styles.green : styles.red);

		doc.insertString(doc.getLength(), "\n", styles.black);
	}

	// ===== Table Operations =====

	private void clearTable() {
		SwingUtilities.invokeLater(() -> {
			model.setRowCount(0);
			endpointMap.clear();
			packetMap.clear();
			resultsMap.clear();
			detailArea.setText("");
			headerPane.setText("");
		});
	}

	private void scanHistory() {
		new Thread(() -> {
			try {
				clearTable();
				List<Packet> packets = Packets.getInstance().queryAll();
				Map<Long, Packet> requestMap = new HashMap<>();

				for (Packet p : packets) {
					if (p.getDirection() == Packet.Direction.CLIENT) {
						requestMap.put(p.getGroup(), p);
					}
				}

				for (Packet p : packets) {
					if (p.getDirection() == Packet.Direction.SERVER) {
						Packet req = requestMap.get(p.getGroup());
						if (req != null) {
							analyzePacket(p, req);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}).start();
	}

	// ===== Packet Analysis =====

	private void analyzePacket(Packet resPacket, Packet reqPacket) {
		try {
			Http resHttp = Http.create(resPacket.getDecodedData());
			Http reqHttp = Http.create(reqPacket.getDecodedData());

			String method = reqHttp.getMethod();
			String host = reqHttp.getHeader().getValue("Host").orElse(reqPacket.getServerName());
			String path = reqHttp.getPath();
			String url = (reqPacket.getUseSSL() ? "https://" : "http://") + host + path;
			String statusCode = resHttp.getStatusCode();
			String endpointKey = method + " " + url + " " + statusCode;

			HttpHeader header = resHttp.getHeader();

			// Run all security checks
			Map<String, Object> context = new HashMap<>();
			Map<String, SecurityCheckResult> results = new LinkedHashMap<>();

			for (SecurityCheck check : SECURITY_CHECKS) {
				SecurityCheckResult result = check.check(header, context);
				results.put(check.getName(), result);
			}

			// Calculate overall status
			boolean overallOk = calculateOverallStatus(results);
			String status = overallOk ? "PASS" : "FAIL";

			// Build row data
			List<Object> rowData = new ArrayList<>();
			rowData.add(method);
			rowData.add(url);
			rowData.add(statusCode);
			for (SecurityCheck check : SECURITY_CHECKS) {
				SecurityCheckResult result = results.get(check.getName());
				rowData.add(result != null ? result.getDisplayValue() : "");
			}
			rowData.add(status);

			Object[] rowArray = rowData.toArray();

			SwingUtilities.invokeLater(() -> {
				if (endpointMap.containsKey(endpointKey)) {
					int row = endpointMap.get(endpointKey);
					for (int i = 0; i < rowArray.length; i++) {
						model.setValueAt(rowArray[i], row, i);
					}
				} else {
					model.addRow(rowArray);
					endpointMap.put(endpointKey, model.getRowCount() - 1);
				}
				packetMap.put(endpointKey, resPacket);
				resultsMap.put(endpointKey, results);
			});

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private boolean calculateOverallStatus(Map<String, SecurityCheckResult> results) {
		for (int i = 0; i < SECURITY_CHECKS.size(); i++) {
			SecurityCheck check = SECURITY_CHECKS.get(i);
			if (!check.affectsOverallStatus()) {
				continue;
			}

			SecurityCheckResult result = results.get(check.getName());
			if (result != null && (result.isFail() || result.isWarn())) {
				return false;
			}
		}
		return true;
	}

	// ===== Table Renderer =====

	private static final int FIXED_COLUMNS = 3; // Method, URL, Code
	private static final Color COLOR_FAIL = new Color(200, 0, 0);
	private static final Color COLOR_WARN = new Color(220, 130, 0);
	private static final Color COLOR_OK = new Color(0, 100, 0);
	private static final Color COLOR_FAIL_BG = new Color(255, 240, 240);

	class SecurityHeaderRenderer extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {
			Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

			int modelRow = table.convertRowIndexToModel(row);
			String endpointKey = buildEndpointKey(modelRow);
			Map<String, SecurityCheckResult> results = resultsMap.get(endpointKey);

			applyBackgroundColor(c, results, isSelected);
			applyForegroundColor(c, column, results, isSelected);

			return c;
		}

		private String buildEndpointKey(int modelRow) {
			String method = (String) model.getValueAt(modelRow, 0);
			String url = (String) model.getValueAt(modelRow, 1);
			String code = (String) model.getValueAt(modelRow, 2);
			return method + " " + url + " " + code;
		}

		private void applyBackgroundColor(Component c, Map<String, SecurityCheckResult> results, boolean isSelected) {
			if (isSelected) return;

			boolean hasFail = results != null && results.values().stream()
					.anyMatch(r -> r.isFail() || r.isWarn());
			c.setBackground(hasFail ? COLOR_FAIL_BG : Color.WHITE);
		}

		private void applyForegroundColor(Component c, int column, Map<String, SecurityCheckResult> results, boolean isSelected) {
			if (isSelected) {
				c.setForeground(table.getSelectionForeground());
				return;
			}

			// Fixed columns (Method, URL, Code)
			if (column < FIXED_COLUMNS) {
				c.setForeground(Color.BLACK);
				return;
			}

			// Check columns
			int checkIndex = column - FIXED_COLUMNS;
			if (checkIndex < SECURITY_CHECKS.size() && results != null) {
				SecurityCheck check = SECURITY_CHECKS.get(checkIndex);
				SecurityCheckResult result = results.get(check.getName());
				applyResultStyle(c, result);
			} else {
				c.setForeground(Color.BLACK);
			}
		}

		private void applyResultStyle(Component c, SecurityCheckResult result) {
			if (result == null) {
				c.setForeground(Color.BLACK);
				return;
			}

			if (result.isFail()) {
				c.setForeground(COLOR_FAIL);
				c.setFont(c.getFont().deriveFont(Font.BOLD));
			} else if (result.isWarn()) {
				c.setForeground(COLOR_WARN);
				c.setFont(c.getFont().deriveFont(Font.BOLD));
			} else if (result.isOk()) {
				c.setForeground(COLOR_OK);
			} else {
				c.setForeground(Color.BLACK);
			}
		}
	}
}
