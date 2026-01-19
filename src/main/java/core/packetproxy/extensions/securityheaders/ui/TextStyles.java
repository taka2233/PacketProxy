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

import java.awt.Color;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

/**
 * Text styles for security headers display. Provides predefined color and style
 * attributes for highlighting security check results.
 */
public final class TextStyles {
	private final SimpleAttributeSet green;
	private final SimpleAttributeSet red;
	private final SimpleAttributeSet yellow;
	private final SimpleAttributeSet black;
	private final SimpleAttributeSet bold;

	public TextStyles() {
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

	public SimpleAttributeSet getGreen() {
		return green;
	}

	public SimpleAttributeSet getRed() {
		return red;
	}

	public SimpleAttributeSet getYellow() {
		return yellow;
	}

	public SimpleAttributeSet getBlack() {
		return black;
	}

	public SimpleAttributeSet getBold() {
		return bold;
	}
}
