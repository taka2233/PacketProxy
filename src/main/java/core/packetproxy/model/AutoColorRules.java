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

import static packetproxy.model.PropertyChangeEventType.AUTO_COLOR_RULES;
import static packetproxy.model.PropertyChangeEventType.DATABASE_MESSAGE;
import static packetproxy.util.Logging.errWithStackTrace;

import com.j256.ormlite.dao.Dao;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;
import java.util.Optional;
import javax.swing.JOptionPane;
import packetproxy.model.Database.DatabaseMessage;

public class AutoColorRules implements PropertyChangeListener {

	private static AutoColorRules instance;
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	public static AutoColorRules getInstance() throws Exception {
		if (instance == null) {
			instance = new AutoColorRules();
		}
		return instance;
	}

	/**
	 * この機能が利用可能かどうかをチェックする
	 * 古いプロジェクトファイルではテーブルが存在しないため利用不可
	 * @return 利用可能な場合true
	 */
	public static boolean isAvailable() {
		try {
			return getInstance().tableExists;
		} catch (Exception e) {
			return false;
		}
	}

	private Database database;
	private Dao<AutoColorRule, Integer> dao;
	private DaoQueryCache<AutoColorRule> cache;
	private boolean tableExists = false;

	private AutoColorRules() throws Exception {
		database = Database.getInstance();
		dao = database.createTable(AutoColorRule.class, this);
		cache = new DaoQueryCache<>();
		tableExists = checkTableExists();
		if (tableExists && !isLatestVersion()) {
			RecreateTable();
		}
	}

	/**
	 * テーブルが存在するかチェック
	 */
	private boolean checkTableExists() {
		try {
			String[] firstResult = dao.queryRaw("SELECT name FROM sqlite_master WHERE type='table' AND name='autoColorRules'").getFirstResult();
			return firstResult != null && firstResult.length > 0 && firstResult[0] != null;
		} catch (Exception e) {
			return false;
		}
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(listener);
	}

	public void create(AutoColorRule rule) throws Exception {
		if (!tableExists) {
			throw new IllegalStateException("Auto color rules table does not exist in this project");
		}
		dao.createIfNotExists(rule);
		cache.clear();
		firePropertyChange();
	}

	public void delete(int id) throws Exception {
		dao.deleteById(id);
		cache.clear();
		firePropertyChange();
	}

	public void delete(AutoColorRule rule) throws Exception {
		dao.delete(rule);
		cache.clear();
		firePropertyChange();
	}

	public void update(AutoColorRule rule) throws Exception {
		dao.update(rule);
		cache.clear();
		firePropertyChange();
	}

	public void refresh() {
		firePropertyChange();
	}

	public AutoColorRule query(int id) throws Exception {
		return dao.queryForId(id);
	}

	public List<AutoColorRule> queryByPattern(String pattern) throws Exception {
		return dao.queryBuilder().where().eq("pattern", pattern).query();
	}

	public List<AutoColorRule> queryAll() throws Exception {
		List<AutoColorRule> ret = cache.query("queryAll", 0);
		if (ret != null) {
			return ret;
		}
		ret = dao.queryBuilder().query();
		cache.set("queryAll", 0, ret);
		return ret;
	}

	public List<AutoColorRule> queryEnabled() throws Exception {
		List<AutoColorRule> ret = cache.query("queryEnabled", 0);
		if (ret != null) {
			return ret;
		}
		ret = dao.queryBuilder().where().eq("enabled", true).query();
		cache.set("queryEnabled", 0, ret);
		return ret;
	}

	/**
	 * エンドポイントに一致するルールを検索し、最初に一致した色を返す
	 * @param endpoint マッチング対象のエンドポイント（例: "https://example.com/api/users"）
	 * @return 一致したルールの色、一致しない場合はOptional.empty()
	 */
	public Optional<AutoColorRule.Color> findMatchingColor(String endpoint) throws Exception {
		for (AutoColorRule rule : queryEnabled()) {
			if (rule.matches(endpoint)) {
				return Optional.of(rule.getColor());
			}
		}
		return Optional.empty();
	}

	/**
	 * エンドポイントに一致するすべてのルールを返す
	 * @param endpoint マッチング対象のエンドポイント
	 * @return 一致したルールのリスト
	 */
	public List<AutoColorRule> findMatchingRules(String endpoint) throws Exception {
		return queryEnabled().stream()
				.filter(rule -> rule.matches(endpoint))
				.toList();
	}

	private void firePropertyChange() {
		pcs.firePropertyChange(AUTO_COLOR_RULES.toString(), null, null);
	}

	private void firePropertyChange(Object value) {
		pcs.firePropertyChange(AUTO_COLOR_RULES.toString(), null, value);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (!DATABASE_MESSAGE.matches(evt)) {
			return;
		}

		DatabaseMessage message = (DatabaseMessage) evt.getNewValue();
		try {
			switch (message) {
				case PAUSE:
					// TODO ロックを取る
					break;
				case RESUME:
					// TODO ロックを解除
					break;
				case DISCONNECT_NOW:
					break;
				case RECONNECT:
					database = Database.getInstance();
					dao = database.createTable(AutoColorRule.class, this);
					cache.clear();
					firePropertyChange(message);
					break;
				case RECREATE:
					database = Database.getInstance();
					dao = database.createTable(AutoColorRule.class, this);
					cache.clear();
					break;
				default:
					break;
			}
		} catch (Exception e) {
			errWithStackTrace(e);
		}
	}

	private boolean isLatestVersion() throws Exception {
		String[] firstResult = dao.queryRaw("SELECT sql FROM sqlite_master WHERE name='autoColorRules'").getFirstResult();
		if (firstResult == null || firstResult.length == 0 || firstResult[0] == null) {
			// テーブルが新規作成された場合は最新とみなす
			return true;
		}
		return firstResult[0].equals(
				"CREATE TABLE `autoColorRules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `enabled` BOOLEAN , `pattern` VARCHAR , `color` VARCHAR ,  UNIQUE (`pattern`))");
	}

	private void RecreateTable() throws Exception {
		int option = JOptionPane.showConfirmDialog(null,
				"autoColorRulesテーブルの形式が更新されているため\n現在のテーブルを削除して再起動しても良いですか？",
				"テーブルの更新", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (option == JOptionPane.YES_OPTION) {
			database.dropTable(AutoColorRule.class);
			dao = database.createTable(AutoColorRule.class, this);
			cache.clear();
		}
	}
}

