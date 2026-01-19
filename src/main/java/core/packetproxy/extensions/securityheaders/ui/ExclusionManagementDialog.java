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
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import packetproxy.extensions.securityheaders.exclusion.ExclusionRule;
import packetproxy.extensions.securityheaders.exclusion.ExclusionRuleManager;
import packetproxy.extensions.securityheaders.exclusion.ExclusionRuleType;

/**
 * Dialog for managing exclusion rules. Provides UI for adding, editing, and
 * deleting exclusion rules.
 */
public final class ExclusionManagementDialog {

	private final ExclusionRuleManager exclusionRuleManager;
	private final JDialog dialog;
	private final DefaultTableModel exclusionTableModel;
	private final JTable exclusionTable;

	public ExclusionManagementDialog(Frame parent, ExclusionRuleManager exclusionRuleManager) {
		this.exclusionRuleManager = exclusionRuleManager;
		this.dialog = new JDialog(parent, "Manage Exclusions", true);
		this.dialog.setLayout(new BorderLayout());
		this.dialog.setSize(600, 400);
		this.dialog.setLocationRelativeTo(parent);

		// Table model for exclusions
		String[] columnNames = {"Type", "Pattern"};
		this.exclusionTableModel = new DefaultTableModel(columnNames, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};

		// Populate table with current rules
		refreshTable();

		this.exclusionTable = new JTable(exclusionTableModel);
		this.exclusionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		this.exclusionTable.getColumnModel().getColumn(0).setPreferredWidth(80);
		this.exclusionTable.getColumnModel().getColumn(1).setPreferredWidth(400);

		JScrollPane scrollPane = new JScrollPane(exclusionTable);
		this.dialog.add(scrollPane, BorderLayout.CENTER);

		// Button panel
		JPanel buttonPanel = createButtonPanel();
		this.dialog.add(buttonPanel, BorderLayout.SOUTH);
	}

	private JPanel createButtonPanel() {
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

		JButton addButton = new JButton("Add");
		addButton.addActionListener(e -> handleAdd());
		buttonPanel.add(addButton);

		JButton editButton = new JButton("Edit");
		editButton.addActionListener(e -> handleEdit());
		buttonPanel.add(editButton);

		JButton deleteButton = new JButton("Delete");
		deleteButton.addActionListener(e -> handleDelete());
		buttonPanel.add(deleteButton);

		JButton clearAllButton = new JButton("Clear All");
		clearAllButton.addActionListener(e -> handleClearAll());
		buttonPanel.add(clearAllButton);

		JButton closeButton = new JButton("Close");
		closeButton.addActionListener(e -> dialog.dispose());
		buttonPanel.add(closeButton);

		return buttonPanel;
	}

	private void handleAdd() {
		ExclusionRule newRule = showEditDialog(null);
		if (newRule != null) {
			exclusionRuleManager.addRule(newRule);
			refreshTable();
		}
	}

	private void handleEdit() {
		int selectedRow = exclusionTable.getSelectedRow();
		if (selectedRow == -1) {
			JOptionPane.showMessageDialog(dialog, "Please select a rule to edit.", "No Selection",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		List<ExclusionRule> rules = exclusionRuleManager.getRules();
		if (selectedRow < rules.size()) {
			ExclusionRule oldRule = rules.get(selectedRow);
			ExclusionRule editedRule = showEditDialog(oldRule);
			if (editedRule != null) {
				exclusionRuleManager.updateRule(oldRule.getId(), editedRule.getType(), editedRule.getPattern());
				refreshTable();
			}
		}
	}

	private void handleDelete() {
		int selectedRow = exclusionTable.getSelectedRow();
		if (selectedRow == -1) {
			JOptionPane.showMessageDialog(dialog, "Please select a rule to delete.", "No Selection",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		List<ExclusionRule> rules = exclusionRuleManager.getRules();
		if (selectedRow < rules.size()) {
			ExclusionRule rule = rules.get(selectedRow);
			exclusionRuleManager.removeRule(rule.getId());
			refreshTable();
		}
	}

	private void handleClearAll() {
		int confirm = JOptionPane.showConfirmDialog(dialog, "Are you sure you want to delete all exclusion rules?",
				"Confirm Clear All", JOptionPane.YES_NO_OPTION);
		if (confirm == JOptionPane.YES_OPTION) {
			exclusionRuleManager.clearRules();
			refreshTable();
		}
	}

	private void refreshTable() {
		exclusionTableModel.setRowCount(0);
		for (ExclusionRule rule : exclusionRuleManager.getRules()) {
			exclusionTableModel.addRow(new Object[]{rule.getType().getDisplayName(), rule.getPattern()});
		}
	}

	private ExclusionRule showEditDialog(ExclusionRule existingRule) {
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.anchor = GridBagConstraints.WEST;

		// Type selector
		gbc.gridx = 0;
		gbc.gridy = 0;
		panel.add(new JLabel("Type:"), gbc);

		JComboBox<ExclusionRuleType> typeCombo = new JComboBox<>(ExclusionRuleType.values());
		gbc.gridx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		panel.add(typeCombo, gbc);

		// Pattern field
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.fill = GridBagConstraints.NONE;
		panel.add(new JLabel("Pattern:"), gbc);

		JTextField patternField = new JTextField(30);
		gbc.gridx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		panel.add(patternField, gbc);

		// Hint label
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.gridwidth = 2;
		JLabel hintLabel = new JLabel(
				"<html><i>Host: example.com | Path: /api/* | Endpoint: GET https://...</i></html>");
		hintLabel.setForeground(Color.GRAY);
		panel.add(hintLabel, gbc);

		// Populate with existing values if editing
		if (existingRule != null) {
			typeCombo.setSelectedItem(existingRule.getType());
			patternField.setText(existingRule.getPattern());
		}

		String title = existingRule == null ? "Add Exclusion Rule" : "Edit Exclusion Rule";
		int result = JOptionPane.showConfirmDialog(dialog, panel, title, JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);

		if (result == JOptionPane.OK_OPTION) {
			String pattern = patternField.getText().trim();
			if (!pattern.isEmpty()) {
				ExclusionRuleType type = (ExclusionRuleType) typeCombo.getSelectedItem();
				if (existingRule != null) {
					return new ExclusionRule(existingRule.getId(), type, pattern);
				}
				return new ExclusionRule(type, pattern);
			}
		}

		return null;
	}

	public void show() {
		refreshTable();
		dialog.setVisible(true);
	}
}
