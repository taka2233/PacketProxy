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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;
import packetproxy.extensions.securityheaders.SecurityCheck;
import packetproxy.extensions.securityheaders.SecurityCheckResult;
import packetproxy.extensions.securityheaders.checks.CookieCheck;
import packetproxy.http.HttpHeader;

/**
 * Detail panel for displaying HTTP headers and security check results. Provides
 * styled text display with color coding for security check results.
 */
public final class SecurityHeadersDetailPanel {

	private final JTextPane headerPane;
	private final JTextPane detailArea;
	private final List<SecurityCheck> securityChecks;
	private final TextStyles textStyles;

	public SecurityHeadersDetailPanel(List<SecurityCheck> securityChecks) {
		this.securityChecks = securityChecks;
		this.textStyles = new TextStyles();

		this.headerPane = new JTextPane();
		this.headerPane.setEditable(false);
		this.headerPane.setBackground(Color.WHITE);

		this.detailArea = new JTextPane();
		this.detailArea.setEditable(false);
		this.detailArea.setBackground(Color.WHITE);
	}

	public JSplitPane createPanel() {
		JScrollPane headerScrollPane = new JScrollPane(headerPane);
		JScrollPane detailScrollPane = new JScrollPane(detailArea);

		JSplitPane bottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, headerScrollPane, detailScrollPane);
		bottomSplit.setResizeWeight(0.5);

		return bottomSplit;
	}

	public void populateHeaders(HttpHeader header, Map<String, SecurityCheckResult> results) {
		try {
			StyledDocument doc = headerPane.getStyledDocument();
			headerPane.setText("");

			// Status line
			doc.insertString(doc.getLength(), header.getStatusline() + "\n", textStyles.getBold());

			// All headers with color coding
			byte[] headerBytes = header.toByteArray();
			String rawHeaders = new String(headerBytes, "UTF-8");
			String[] lines = rawHeaders.split("\r\n|\n");

			for (String line : lines) {
				if (line.isEmpty())
					continue;

				// Try segment-based highlighting first
				List<SecurityCheck.HighlightSegment> allSegments = collectHighlightSegments(line, results);

				if (!allSegments.isEmpty()) {
					// Sort segments by start position
					allSegments.sort((a, b) -> Integer.compare(a.getStart(), b.getStart()));
					insertLineWithSegments(doc, line, allSegments);
				} else {
					// Fall back to whole-line highlighting
					SimpleAttributeSet style = getStyleForHeaderLine(line, results);
					doc.insertString(doc.getLength(), line + "\n", style);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void populateIssues(Map<String, SecurityCheckResult> results) {
		try {
			StyledDocument doc = detailArea.getStyledDocument();
			detailArea.setText("");

			doc.insertString(doc.getLength(), "Security Check Results\n", textStyles.getBold());
			doc.insertString(doc.getLength(), "=".repeat(40) + "\n\n", textStyles.getBlack());

			// Display results for each check
			for (SecurityCheck check : securityChecks) {
				SecurityCheckResult result = results.get(check.getName());
				if (result != null) {
					writeCheckResult(doc, check, result);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private List<SecurityCheck.HighlightSegment> collectHighlightSegments(String line,
			Map<String, SecurityCheckResult> results) {
		List<SecurityCheck.HighlightSegment> allSegments = new ArrayList<>();

		for (SecurityCheck check : securityChecks) {
			if (!check.matchesHeaderLine(line.toLowerCase()))
				continue;

			SecurityCheckResult result = results.get(check.getName());
			List<SecurityCheck.HighlightSegment> segments = check.getHighlightSegments(line, result);
			allSegments.addAll(segments);
		}

		return allSegments;
	}

	private void insertLineWithSegments(StyledDocument doc, String line, List<SecurityCheck.HighlightSegment> segments)
			throws Exception {
		int currentPos = 0;
		int lineLength = line.length();

		for (SecurityCheck.HighlightSegment segment : segments) {
			int start = segment.getStart();
			int end = segment.getEnd();

			// Validate segment bounds
			if (start < 0 || end < 0 || start > lineLength || end > lineLength || start > end) {
				continue;
			}

			// Insert text before this segment (black)
			if (start > currentPos) {
				String beforeText = line.substring(currentPos, start);
				doc.insertString(doc.getLength(), beforeText, textStyles.getBlack());
			}

			// Insert the segment with appropriate style
			String segmentText = line.substring(start, end);
			SimpleAttributeSet style = getStyleForHighlightType(segment.getType());
			doc.insertString(doc.getLength(), segmentText, style);
			currentPos = end;
		}

		// Insert remaining text after last segment (black)
		if (currentPos < line.length()) {
			doc.insertString(doc.getLength(), line.substring(currentPos), textStyles.getBlack());
		}

		doc.insertString(doc.getLength(), "\n", textStyles.getBlack());
	}

	private SimpleAttributeSet getStyleForHighlightType(SecurityCheck.HighlightType type) {
		switch (type) {
			case GREEN :
				return textStyles.getGreen();
			case RED :
				return textStyles.getRed();
			case YELLOW :
				return textStyles.getYellow();
			default :
				return textStyles.getBlack();
		}
	}

	private SimpleAttributeSet getStyleForHeaderLine(String line, Map<String, SecurityCheckResult> results) {
		for (SecurityCheck check : securityChecks) {
			SecurityCheckResult result = results.get(check.getName());
			SecurityCheck.HighlightType type = check.getHighlightType(line, result);
			if (type == SecurityCheck.HighlightType.GREEN) {
				return textStyles.getGreen();
			} else if (type == SecurityCheck.HighlightType.RED) {
				return textStyles.getRed();
			}
		}

		// Special handling for Set-Cookie (per-line check)
		String lowerLine = line.toLowerCase();
		if (lowerLine.startsWith("set-cookie:")) {
			return CookieCheck.hasSecureFlag(lowerLine) ? textStyles.getGreen() : textStyles.getRed();
		}

		return textStyles.getBlack();
	}

	private void writeCheckResult(StyledDocument doc, SecurityCheck check, SecurityCheckResult result)
			throws Exception {
		doc.insertString(doc.getLength(), check.getName() + ": ", textStyles.getBold());

		if (result.isOk()) {
			doc.insertString(doc.getLength(), "OK\n", textStyles.getGreen());
			doc.insertString(doc.getLength(), "  " + result.getDisplayValue() + "\n\n", textStyles.getBlack());
		} else if (result.isWarn()) {
			doc.insertString(doc.getLength(), "WARNING\n", textStyles.getYellow());
			doc.insertString(doc.getLength(), "  " + check.getMissingMessage() + "\n", textStyles.getYellow());
			doc.insertString(doc.getLength(), "  Current: " + result.getDisplayValue() + "\n\n", textStyles.getBlack());
		} else {
			doc.insertString(doc.getLength(), "FAIL\n", textStyles.getRed());
			doc.insertString(doc.getLength(), "  " + check.getMissingMessage() + "\n", textStyles.getRed());
			doc.insertString(doc.getLength(), "  Current: " + result.getDisplayValue() + "\n\n", textStyles.getBlack());
		}
	}
}
