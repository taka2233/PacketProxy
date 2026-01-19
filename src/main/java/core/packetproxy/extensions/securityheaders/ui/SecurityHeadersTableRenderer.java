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
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import packetproxy.extensions.securityheaders.SecurityCheck;
import packetproxy.extensions.securityheaders.SecurityCheckResult;

/**
 * Custom table renderers for security headers extension. Provides header
 * renderer and cell renderer for security check results.
 */
public final class SecurityHeadersTableRenderer {

	public static final int FIXED_COLUMNS = 3; // Method, URL, Code
	private static final Color COLOR_FAIL = new Color(200, 0, 0);
	private static final Color COLOR_WARN = new Color(220, 130, 0);
	private static final Color COLOR_OK = new Color(0, 100, 0);
	private static final Color COLOR_FAIL_BG = new Color(255, 240, 240);
	private static final Color COLOR_WARN_BG = new Color(255, 250, 230);

	/** Custom header renderer: left-aligned text with sort icon on the right */
	public static class HeaderRenderer extends JPanel implements TableCellRenderer {
		private final JLabel textLabel;
		private final JLabel iconLabel;
		private final TableCellRenderer defaultRenderer;

		public HeaderRenderer(JTable table) {
			this.defaultRenderer = table.getTableHeader().getDefaultRenderer();
			setLayout(new BorderLayout());
			setOpaque(true);

			textLabel = new JLabel();
			textLabel.setHorizontalAlignment(SwingConstants.LEFT);

			iconLabel = new JLabel();
			iconLabel.setHorizontalAlignment(SwingConstants.RIGHT);

			add(textLabel, BorderLayout.CENTER);
			add(iconLabel, BorderLayout.EAST);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {
			// Get default component to extract styling
			Component defaultComponent = defaultRenderer.getTableCellRendererComponent(table, value, isSelected,
					hasFocus, row, column);

			// Copy background and border from default renderer
			setBackground(defaultComponent.getBackground());
			setForeground(defaultComponent.getForeground());
			setFont(defaultComponent.getFont());
			if (defaultComponent instanceof JComponent) {
				setBorder(((JComponent) defaultComponent).getBorder());
			}

			// Set text
			textLabel.setText(value != null ? value.toString() : "");
			textLabel.setFont(getFont());
			textLabel.setForeground(getForeground());

			// Get sort icon from default renderer
			iconLabel.setIcon(null);
			if (defaultComponent instanceof JLabel) {
				Icon icon = ((JLabel) defaultComponent).getIcon();
				iconLabel.setIcon(icon);
			}

			return this;
		}
	}

	/** Cell renderer for security check results with color coding */
	public static class SecurityHeaderRenderer extends DefaultTableCellRenderer {
		private final JTable table;
		private final DefaultTableModel model;
		private final List<SecurityCheck> securityChecks;
		private final Map<String, Map<String, SecurityCheckResult>> resultsMap;

		public SecurityHeaderRenderer(JTable table, DefaultTableModel model, List<SecurityCheck> securityChecks,
				Map<String, Map<String, SecurityCheckResult>> resultsMap) {
			this.table = table;
			this.model = model;
			this.securityChecks = securityChecks;
			this.resultsMap = resultsMap;
		}

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
			if (isSelected)
				return;

			if (results == null) {
				c.setBackground(Color.WHITE);
				return;
			}

			boolean hasFail = results.values().stream().anyMatch(SecurityCheckResult::isFail);
			boolean hasWarn = results.values().stream().anyMatch(SecurityCheckResult::isWarn);

			if (hasFail) {
				c.setBackground(COLOR_FAIL_BG);
			} else if (hasWarn) {
				c.setBackground(COLOR_WARN_BG);
			} else {
				c.setBackground(Color.WHITE);
			}
		}

		private void applyForegroundColor(Component c, int column, Map<String, SecurityCheckResult> results,
				boolean isSelected) {
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
			if (checkIndex < securityChecks.size() && results != null) {
				SecurityCheck check = securityChecks.get(checkIndex);
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
