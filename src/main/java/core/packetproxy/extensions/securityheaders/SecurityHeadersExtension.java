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

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import packetproxy.extensions.securityheaders.checks.*;
import packetproxy.extensions.securityheaders.exclusion.ExclusionRule;
import packetproxy.extensions.securityheaders.exclusion.ExclusionRuleManager;
import packetproxy.extensions.securityheaders.exclusion.ExclusionRuleType;
import packetproxy.extensions.securityheaders.ui.SecurityHeadersDetailPanel;
import packetproxy.extensions.securityheaders.ui.SecurityHeadersTableRenderer;
import packetproxy.extensions.securityheaders.ui.SecurityHeadersToolbar;
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
	private TableRowSorter<DefaultTableModel> sorter;
	private Map<String, Integer> endpointMap;
	private Map<String, Packet> packetMap;
	private Map<String, Map<String, SecurityCheckResult>> resultsMap;
	private JPopupMenu contextMenu;
	private final ExclusionRuleManager exclusionRuleManager;
	private SecurityHeadersToolbar toolbar;
	private SecurityHeadersDetailPanel detailPanel;

	public SecurityHeadersExtension() {
		super();
		this.setName("SecurityHeaders");
		this.endpointMap = new HashMap<>();
		this.packetMap = new HashMap<>();
		this.resultsMap = new HashMap<>();
		this.exclusionRuleManager = ExclusionRuleManager.getInstance();
		this.exclusionRuleManager.addChangeListener(rules -> {
			if (toolbar != null) {
				toolbar.applyFilter();
			}
		});
	}

	@Override
	public JComponent createPanel() throws Exception {
		JPanel panel = new JPanel(new BorderLayout());

		initializeTableModel();
		initializeTable();

		// Create toolbar with callbacks
		this.toolbar = new SecurityHeadersToolbar(exclusionRuleManager, this::scanHistory, this::clearTable);
		this.toolbar.setSorter(sorter);
		panel.add(toolbar.getPanel(), BorderLayout.NORTH);

		// Create detail panel
		this.detailPanel = new SecurityHeadersDetailPanel(SECURITY_CHECKS);

		JScrollPane tableScrollPane = new JScrollPane(table);
		JSplitPane bottomSplit = detailPanel.createPanel();

		JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScrollPane, bottomSplit);
		mainSplit.setDividerLocation(300);
		panel.add(mainSplit, BorderLayout.CENTER);

		setupSelectionListener();

		return panel;
	}

	// ===== UI Component Creation =====

	private void initializeTableModel() {
		// Build columns dynamically from registered checks
		List<String> columns = new ArrayList<>();
		columns.add("Method");
		columns.add("URL");
		columns.add("Server Response");
		for (SecurityCheck check : SECURITY_CHECKS) {
			columns.add(check.getColumnName());
		}

		model = new DefaultTableModel(columns.toArray(new String[0]), 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
	}

	private void initializeTable() {
		table = new JTable(model);

		// Set up TableRowSorter for filtering
		sorter = new TableRowSorter<>(model);
		table.setRowSorter(sorter);

		// Set custom header renderer (left-aligned text, sort icon on right)
		table.getTableHeader().setDefaultRenderer(new SecurityHeadersTableRenderer.HeaderRenderer(table));

		// Set custom cell renderer
		table.setDefaultRenderer(Object.class,
				new SecurityHeadersTableRenderer.SecurityHeaderRenderer(table, model, SECURITY_CHECKS, resultsMap));

		// Set column widths
		table.getColumnModel().getColumn(0).setPreferredWidth(50); // Method
		table.getColumnModel().getColumn(1).setPreferredWidth(300); // URL
		table.getColumnModel().getColumn(2).setPreferredWidth(60); // HTTP Status Code
		// Security check columns
		for (int i = 0; i < SECURITY_CHECKS.size(); i++) {
			table.getColumnModel().getColumn(SecurityHeadersTableRenderer.FIXED_COLUMNS + i).setPreferredWidth(80);
		}

		// Default sort by URL ascending
		SwingUtilities.invokeLater(() -> {
			List<SortKey> sortKeys = new ArrayList<>();
			sortKeys.add(new SortKey(1, SortOrder.ASCENDING)); // URL column
			sorter.setSortKeys(sortKeys);
		});

		// Setup context menu for right-click
		setupContextMenu();
	}

	private void setupContextMenu() {
		contextMenu = new JPopupMenu();

		JMenuItem excludeHostItem = new JMenuItem("Exclude this Host");
		excludeHostItem.addActionListener(e -> excludeSelectedHost());
		contextMenu.add(excludeHostItem);

		JMenuItem excludePathItem = new JMenuItem("Exclude this Path");
		excludePathItem.addActionListener(e -> excludeSelectedPath());
		contextMenu.add(excludePathItem);

		JMenuItem excludeEndpointItem = new JMenuItem("Exclude this Endpoint");
		excludeEndpointItem.addActionListener(e -> excludeSelectedEndpoint());
		contextMenu.add(excludeEndpointItem);

		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				handleContextMenuTrigger(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				handleContextMenuTrigger(e);
			}

			private void handleContextMenuTrigger(MouseEvent e) {
				if (e.isPopupTrigger()) {
					int row = table.rowAtPoint(e.getPoint());
					if (row >= 0 && row < table.getRowCount()) {
						table.setRowSelectionInterval(row, row);
					}
					contextMenu.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		});
	}

	private void excludeSelectedHost() {
		int viewRow = table.getSelectedRow();
		if (viewRow == -1)
			return;

		int modelRow = table.convertRowIndexToModel(viewRow);
		String url = (String) model.getValueAt(modelRow, 1);
		String host = extractHostFromUrl(url);

		if (host != null) {
			exclusionRuleManager.addRule(new ExclusionRule(ExclusionRuleType.HOST, host));
		}
	}

	private void excludeSelectedPath() {
		int viewRow = table.getSelectedRow();
		if (viewRow == -1)
			return;

		int modelRow = table.convertRowIndexToModel(viewRow);
		String url = (String) model.getValueAt(modelRow, 1);
		String path = extractPathFromUrl(url);

		if (path != null) {
			exclusionRuleManager.addRule(new ExclusionRule(ExclusionRuleType.PATH, path));
		}
	}

	private void excludeSelectedEndpoint() {
		int viewRow = table.getSelectedRow();
		if (viewRow == -1)
			return;

		int modelRow = table.convertRowIndexToModel(viewRow);
		String method = (String) model.getValueAt(modelRow, 0);
		String url = (String) model.getValueAt(modelRow, 1);

		String endpoint = method + " " + url;
		exclusionRuleManager.addRule(new ExclusionRule(ExclusionRuleType.ENDPOINT, endpoint));
	}

	private String extractHostFromUrl(String url) {
		try {
			URI uri = new URI(url);
			return uri.getHost();
		} catch (Exception e) {
			return null;
		}
	}

	private String extractPathFromUrl(String url) {
		try {
			URI uri = new URI(url);
			String path = uri.getPath();
			return (path == null || path.isEmpty()) ? "/" : path;
		} catch (Exception e) {
			return null;
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

				detailPanel.populateHeaders(header, results);
				detailPanel.populateIssues(results);

			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	// ===== Table Operations =====

	private void clearTable() {
		SwingUtilities.invokeLater(() -> {
			model.setRowCount(0);
			endpointMap.clear();
			packetMap.clear();
			resultsMap.clear();
			if (toolbar != null) {
				toolbar.resetFilters();
			}
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
			String statusCode = resHttp.getStatusCode();

			if (method == null || host == null || path == null || statusCode == null) {
				return;
			}

			String url = (reqPacket.getUseSSL() ? "https://" : "http://") + host + path;
			String endpointKey = method + " " + url + " " + statusCode;

			HttpHeader header = resHttp.getHeader();

			// Run all security checks
			Map<String, Object> context = new HashMap<>();
			// Pass request Origin header for CORS reflection detection
			HttpHeader reqHeader = reqHttp.getHeader();
			reqHeader.getValue("Origin").ifPresent(origin -> context.put("requestOrigin", origin));

			Map<String, SecurityCheckResult> results = new LinkedHashMap<>();

			for (SecurityCheck check : SECURITY_CHECKS) {
				SecurityCheckResult result = check.check(header, context);
				results.put(check.getName(), result);
			}

			// Build row data
			List<Object> rowData = new ArrayList<>();
			rowData.add(method);
			rowData.add(url);
			rowData.add(statusCode);
			for (SecurityCheck check : SECURITY_CHECKS) {
				SecurityCheckResult result = results.get(check.getName());
				rowData.add(result != null ? result.getDisplayValue() : "");
			}

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
}
