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
package packetproxy.extensions.securityheaders.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import packetproxy.extensions.securityheaders.exclusion.ExclusionRuleManager;

/**
 * Toolbar component for security headers extension. Provides buttons and filter
 * controls for the main table.
 */
public final class SecurityHeadersToolbar {

	private static final String[] METHOD_OPTIONS = {"GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"};
	private static final String[] STATUS_CODE_OPTIONS = {"2xx", "3xx", "4xx", "5xx"};

	private final JPanel panel;
	private final List<JCheckBox> methodCheckBoxes;
	private final List<JCheckBox> statusCheckBoxes;
	private JTextField filterField;
	private TableRowSorter<DefaultTableModel> sorter;
	private final ExclusionRuleManager exclusionRuleManager;

	// Callbacks
	private final Runnable onScanHistory;
	private final Runnable onClearTable;

	public SecurityHeadersToolbar(ExclusionRuleManager exclusionRuleManager, Runnable onScanHistory,
			Runnable onClearTable) {
		this.exclusionRuleManager = exclusionRuleManager;
		this.onScanHistory = onScanHistory;
		this.onClearTable = onClearTable;

		this.methodCheckBoxes = new ArrayList<>();
		this.statusCheckBoxes = new ArrayList<>();

		this.panel = createPanel();
	}

	public void setSorter(TableRowSorter<DefaultTableModel> sorter) {
		this.sorter = sorter;
	}

	public JPanel getPanel() {
		return panel;
	}

	public void showExclusionsDialog(Frame parentFrame) {
		ExclusionManagementDialog dialog = new ExclusionManagementDialog(parentFrame, exclusionRuleManager);
		dialog.show();
	}

	public void applyFilter() {
		if (sorter == null || methodCheckBoxes == null || statusCheckBoxes == null || filterField == null) {
			return;
		}

		List<RowFilter<DefaultTableModel, Object>> filters = new ArrayList<>();

		// Method filter (OR between selected methods)
		List<RowFilter<DefaultTableModel, Object>> methodFilters = new ArrayList<>();
		for (JCheckBox cb : methodCheckBoxes) {
			if (cb.isSelected()) {
				methodFilters.add(RowFilter.regexFilter("^" + java.util.regex.Pattern.quote(cb.getText()) + "$", 0));
			}
		}
		if (!methodFilters.isEmpty()) {
			filters.add(RowFilter.orFilter(methodFilters));
		}

		// Status Code filter (OR between selected statuses)
		List<RowFilter<DefaultTableModel, Object>> statusFilters = new ArrayList<>();
		for (JCheckBox cb : statusCheckBoxes) {
			if (cb.isSelected()) {
				String statusPrefix = cb.getText().substring(0, 1); // "2", "3", "4", or "5"
				statusFilters.add(RowFilter.regexFilter("^" + statusPrefix + "\\d{2}$", 2));
			}
		}
		if (!statusFilters.isEmpty()) {
			filters.add(RowFilter.orFilter(statusFilters));
		}

		// Text filter
		String text = filterField.getText().trim();
		if (!text.isEmpty()) {
			filters.add(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
		}

		// Exclusion rules filter
		filters.add(createExclusionFilter());

		// Apply combined filter (AND between method group, status group, text, and
		// exclusions)
		if (sorter == null) {
			return;
		}
		if (filters.isEmpty()) {
			sorter.setRowFilter(null);
		} else {
			sorter.setRowFilter(RowFilter.andFilter(filters));
		}
	}

	public void resetFilters() {
		filterField.setText("");
		methodCheckBoxes.forEach(cb -> cb.setSelected(true));
		statusCheckBoxes.forEach(cb -> cb.setSelected(true));
		applyFilter();
	}

	private RowFilter<DefaultTableModel, Object> createExclusionFilter() {
		return new RowFilter<DefaultTableModel, Object>() {
			@Override
			public boolean include(Entry<? extends DefaultTableModel, ? extends Object> entry) {
				String method = (String) entry.getValue(0);
				String url = (String) entry.getValue(1);
				// Return true to include (NOT excluded), false to exclude
				return !exclusionRuleManager.shouldExclude(method, url);
			}
		};
	}

	private JPanel createPanel() {
		JPanel buttonPanel = new JPanel(new BorderLayout());

		// Left side: buttons
		JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton scanButton = new JButton("Scan History");
		scanButton.addActionListener(e -> onScanHistory.run());
		leftPanel.add(scanButton);

		JButton clearButton = new JButton("Clear");
		clearButton.addActionListener(e -> onClearTable.run());
		leftPanel.add(clearButton);

		JButton exclusionsButton = new JButton("Exclusions");
		exclusionsButton.addActionListener(e -> {
			Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(panel);
			showExclusionsDialog(parentFrame);
		});
		leftPanel.add(exclusionsButton);

		buttonPanel.add(leftPanel, BorderLayout.WEST);

		// Right side: filter
		JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

		// Method filter checkboxes
		filterPanel.add(new JLabel("Method:"));
		for (String method : METHOD_OPTIONS) {
			JCheckBox cb = new JCheckBox(method, true); // default selected
			cb.addActionListener(e -> applyFilter());
			methodCheckBoxes.add(cb);
			filterPanel.add(cb);
		}

		filterPanel.add(Box.createHorizontalStrut(10)); // spacer

		// Status Code filter checkboxes
		filterPanel.add(new JLabel("Server Response:"));
		for (String status : STATUS_CODE_OPTIONS) {
			JCheckBox cb = new JCheckBox(status, true); // default selected
			cb.addActionListener(e -> applyFilter());
			statusCheckBoxes.add(cb);
			filterPanel.add(cb);
		}

		filterPanel.add(Box.createHorizontalStrut(10)); // spacer

		// Text filter
		filterPanel.add(new JLabel("Filter:"));
		filterField = new JTextField(15);
		filterField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				applyFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				applyFilter();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				applyFilter();
			}
		});
		filterPanel.add(filterField);
		buttonPanel.add(filterPanel, BorderLayout.EAST);

		return buttonPanel;
	}
}
