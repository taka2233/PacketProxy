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
package packetproxy.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "autoColorRules")
public class AutoColorRule {

	public enum Color {
		GREEN("green"),
		BROWN("brown"),
		YELLOW("yellow");

		private final String value;

		Color(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		public static Color fromValue(String value) {
			for (Color color : values()) {
				if (color.value.equals(value)) {
					return color;
				}
			}
			return GREEN;
		}
	}

	@DatabaseField(generatedId = true)
	private int id;

	@DatabaseField
	private boolean enabled;

	@DatabaseField(unique = true)
	private String pattern;

	@DatabaseField
	private String color;

	public AutoColorRule() {
		// ORMLite needs a no-arg constructor
	}

	public AutoColorRule(String pattern, Color color) {
		this.enabled = true;
		this.pattern = pattern;
		this.color = color.getValue();
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getPattern() {
		return pattern;
	}

	public void setPattern(String pattern) {
		this.pattern = pattern;
	}

	public String getColorValue() {
		return color;
	}

	public Color getColor() {
		return Color.fromValue(color);
	}

	public void setColor(Color color) {
		this.color = color.getValue();
	}

	public void setColor(String color) {
		this.color = color;
	}

	/**
	 * エンドポイントがパターンに部分一致するかチェック
	 * @param endpoint チェック対象のエンドポイント
	 * @return パターンが含まれていればtrue
	 */
	public boolean matches(String endpoint) {
		if (pattern == null || endpoint == null) {
			return false;
		}
		return endpoint.contains(pattern);
	}

	@Override
	public String toString() {
		return String.format("[%s] %s -> %s", enabled ? "ON" : "OFF", pattern, color);
	}

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		AutoColorRule that = (AutoColorRule) obj;
		return id == that.id;
	}
}

