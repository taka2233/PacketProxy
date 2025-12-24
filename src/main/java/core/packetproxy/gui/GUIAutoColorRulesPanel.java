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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import packetproxy.model.AutoColorRule;
import packetproxy.model.AutoColorRules;

public class GUIAutoColorRulesPanel {

	private JFrame owner;
	private RulesTableModel tableModel;
	private JTable table;
	private JComponent jcomponent;

	public class RulesTableModel extends DefaultTableModel {
		private static final long serialVersionUID = 1L;

		RulesTableModel(String[] columnNames, int rowNum) {
			super(columnNames, rowNum);
		}

		@Override
		public Class<?> getColumnClass(int column) {
			if (getRowCount() == 0) {
				return Object.class;
			}
			Object value = getValueAt(0, column);
			return value != null ? value.getClass() : Object.class;
		}

		@Override
		public boolean isCellEditable(int row, int column) {
			// Enabled column is editable
			return column == 1;
		}

		@Override
		public void setValueAt(Object aValue, int row, int column) {
			super.setValueAt(aValue, row, column);
			if (column == 1) {
				// Update enabled status in database
				try {
					int id = (int) getValueAt(row, 0);
					AutoColorRule rule = AutoColorRules.getInstance().query(id);
					if (rule != null) {
						rule.setEnabled((Boolean) aValue);
						AutoColorRules.getInstance().update(rule);
					}
				} catch (Exception e) {
					errWithStackTrace(e);
				}
			}
		}
	}

	public GUIAutoColorRulesPanel(JFrame owner) throws Exception {
		this.owner = owner;
		jcomponent = createComponent();
		updateImpl();
	}

	public JComponent createPanel() {
		return jcomponent;
	}

	private void tableFixedColumnWidth(JTable table, int[] menu_width, boolean[] fixed_map) {
		for (int index = 0; index < fixed_map.length; index++) {
			if (fixed_map[index]) {
				TableColumn col = table.getColumnModel().getColumn(index);
				col.setMinWidth(menu_width[index]);
				col.setMaxWidth(menu_width[index]);
			}
		}
	}

	private void tableAssignAlignment(JTable table, int[] align_map) {
		class HeaderRenderer implements TableCellRenderer {
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				DefaultTableCellRenderer tcr = (DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer();
				JLabel label = (JLabel) tcr.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				label.setHorizontalAlignment(align_map[column]);
				return label;
			}
		}
		HeaderRenderer hrenderer = new HeaderRenderer();
		for (int index = 0; index < align_map.length; index++) {
			if (index == 1) {
				// Skip checkbox column
				continue;
			}
			DefaultTableCellRenderer crenderer = new DefaultTableCellRenderer();
			crenderer.setHorizontalAlignment(align_map[index]);
			table.getColumnModel().getColumn(index).setCellRenderer(crenderer);
			table.getColumnModel().getColumn(index).setHeaderRenderer(hrenderer);
		}
	}

	private JComponent createComponent() {
		String[] menu = {"#", "Enabled", "Pattern (Regex)", "Color"};
		int[] menu_width = {40, 60, 550, 100};
		boolean[] fixed_map = {true, true, false, true};
		int[] align_map = {JLabel.RIGHT, JLabel.CENTER, JLabel.LEFT, JLabel.CENTER};

		tableModel = new RulesTableModel(menu, 0);

		JPanel panel = new JPanel();

		table = new JTable(tableModel);
		tableFixedColumnWidth(table, menu_width, fixed_map);
		tableAssignAlignment(table, align_map);

		for (int i = 0; i < menu.length; i++) {
			table.getColumn(menu[i]).setPreferredWidth(menu_width[i]);
		}
		((JComponent) table.getDefaultRenderer(Boolean.class)).setOpaque(true);

		// Color column renderer
		table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				String colorValue = (String) value;
				if (!isSelected) {
					switch (colorValue) {
						case "green":
							c.setBackground(new Color(0x7f, 0xff, 0xd4));
							c.setForeground(Color.BLACK);
							break;
						case "brown":
							c.setBackground(new Color(0xd2, 0x69, 0x1e));
							c.setForeground(Color.WHITE);
							break;
						case "yellow":
							c.setBackground(new Color(0xff, 0xd7, 0x00));
							c.setForeground(Color.BLACK);
							break;
						default:
							c.setBackground(Color.WHITE);
							c.setForeground(Color.BLACK);
					}
				}
				return c;
			}
		});

		JScrollPane scrollpane = new JScrollPane(table);
		scrollpane.setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
		scrollpane.setBackground(Color.WHITE);

		panel.add(createTableButton());
		panel.add(scrollpane);
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		panel.setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
		panel.setBackground(Color.WHITE);

		JPanel vpanel = new JPanel();
		vpanel.setLayout(new BoxLayout(vpanel, BoxLayout.Y_AXIS));
		vpanel.setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
		vpanel.add(panel);

		return vpanel;
	}

	private JPanel createTableButton() {
		JButton button_add = new JButton("Add");
		JButton button_edit = new JButton("Edit");
		JButton button_remove = new JButton("Remove");

		int height = button_add.getMaximumSize().height;

		button_add.setMaximumSize(new Dimension(100, height));
		button_edit.setMaximumSize(new Dimension(100, height));
		button_remove.setMaximumSize(new Dimension(100, height));

		JPanel panel = new JPanel();
		panel.add(button_add);
		panel.add(button_edit);
		panel.add(button_remove);

		button_add.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try {
					GUIAutoColorRuleAddDialog dlg = new GUIAutoColorRuleAddDialog(owner);
					dlg.showDialog();
					updateImpl();
				} catch (Exception e1) {
					errWithStackTrace(e1);
				}
			}
		});

		button_edit.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try {
					AutoColorRule rule = getSelectedTableContent();
					if (rule != null) {
						GUIAutoColorRuleEditDialog dlg = new GUIAutoColorRuleEditDialog(owner, rule);
						dlg.showDialog();
						updateImpl();
					}
				} catch (Exception e1) {
					errWithStackTrace(e1);
				}
			}
		});

		button_remove.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try {
					AutoColorRule rule = getSelectedTableContent();
					if (rule != null) {
						AutoColorRules.getInstance().delete(rule);
						updateImpl();
					}
				} catch (Exception e1) {
					errWithStackTrace(e1);
				}
			}
		});

		panel.setMaximumSize(new Dimension(100, Short.MAX_VALUE));
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(Color.WHITE);
		return panel;
	}

	private void updateImpl() throws Exception {
		clearTableContents();
		AutoColorRules.getInstance().queryAll().forEach(rule -> addTableContent(rule));
	}

	private void clearTableContents() {
		tableModel.setRowCount(0);
	}

	private void addTableContent(AutoColorRule rule) {
		tableModel.addRow(new Object[]{rule.getId(), rule.isEnabled(), rule.getPattern(), rule.getColorValue()});
	}

	private AutoColorRule getSelectedTableContent() throws Exception {
		int row = table.getSelectedRow();
		if (row < 0) {
			return null;
		}
		int id = (int) table.getValueAt(row, 0);
		return AutoColorRules.getInstance().query(id);
	}
}

