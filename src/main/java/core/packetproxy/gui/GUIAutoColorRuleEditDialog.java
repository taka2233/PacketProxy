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

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import packetproxy.model.AutoColorRule;
import packetproxy.model.AutoColorRules;

public class GUIAutoColorRuleEditDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private JButton button_cancel = new JButton("Cancel");
	private JButton button_update = new JButton("Update");
	private JTextArea text_pattern = new JTextArea(3, 50);
	private JComboBox<String> combo_color = new JComboBox<>(new String[]{"green", "brown", "yellow"});
	private JCheckBox check_enabled = new JCheckBox("Enabled");
	private int height = 250;
	private int width = 900;
	private AutoColorRule rule;

	private JComponent label_and_object(String label_name, JComponent object) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		JLabel label = new JLabel(label_name);
		label.setPreferredSize(new Dimension(120, label.getMaximumSize().height));
		label.setVerticalAlignment(JLabel.TOP);
		panel.add(label);
		object.setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
		panel.add(object);
		panel.setMaximumSize(new Dimension(Short.MAX_VALUE, 80));
		return panel;
	}

	private JComponent label_and_combo(String label_name, JComponent object) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		JLabel label = new JLabel(label_name);
		label.setPreferredSize(new Dimension(120, label.getMaximumSize().height));
		panel.add(label);
		object.setMaximumSize(new Dimension(Short.MAX_VALUE, label.getMaximumSize().height * 2));
		panel.add(object);
		return panel;
	}

	private JComponent buttons() {
		JPanel panel_button = new JPanel();
		panel_button.setLayout(new BoxLayout(panel_button, BoxLayout.X_AXIS));
		panel_button.setMaximumSize(new Dimension(Short.MAX_VALUE, button_update.getMaximumSize().height));
		panel_button.add(button_cancel);
		panel_button.add(button_update);
		return panel_button;
	}

	public void showDialog() {
		setModal(true);
		setVisible(true);
	}

	public GUIAutoColorRuleEditDialog(JFrame owner, AutoColorRule rule) throws Exception {
		super(owner);
		this.rule = rule;
		setTitle("Edit Auto Color Rule");
		Rectangle rect = owner.getBounds();
		setBounds(rect.x + rect.width / 2 - width / 2, rect.y + rect.height / 2 - height / 2, width, height);

		Container c = getContentPane();
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		// テキストエリアの設定：折り返しを有効にする
		text_pattern.setLineWrap(true);
		text_pattern.setWrapStyleWord(true);
		text_pattern.setText(rule.getPattern());
		combo_color.setSelectedItem(rule.getColorValue());
		check_enabled.setSelected(rule.isEnabled());

		JScrollPane scrollPane = new JScrollPane(text_pattern);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		panel.add(label_and_object("Pattern:", scrollPane));
		panel.add(label_and_combo("Color:", combo_color));
		panel.add(label_and_combo("Status:", check_enabled));
		panel.add(buttons());

		c.add(panel);

		button_cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				dispose();
			}
		});

		button_update.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					String pattern = text_pattern.getText().trim();
					if (pattern.isEmpty()) {
						JOptionPane.showMessageDialog(owner, "Pattern cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
						return;
					}

					AutoColorRule updatedRule = AutoColorRules.getInstance().query(rule.getId());
					updatedRule.setPattern(pattern);
					updatedRule.setColor((String) combo_color.getSelectedItem());
					updatedRule.setEnabled(check_enabled.isSelected());
					AutoColorRules.getInstance().update(updatedRule);
					dispose();
				} catch (Exception e1) {
					errWithStackTrace(e1);
					JOptionPane.showMessageDialog(owner, "Failed to update rule: " + e1.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				}
			}
		});
	}
}
