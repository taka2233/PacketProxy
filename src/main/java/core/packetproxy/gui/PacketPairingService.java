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

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;

/**
 * リクエストとレスポンスのパケットペアリングを管理するサービス。
 * GUIHistoryとGUIPacket間の循環依存を解消するために抽出されたクラス。
 */
public class PacketPairingService {

    private static PacketPairingService instance;

    // グループIDと行番号のマッピング（リクエスト行を追跡）
    private Hashtable<Long, Integer> groupRow;
    // レスポンスが既にマージされているグループID
    private HashSet<Long> groupHasResponse;
    // レスポンスパケットIDとリクエストパケットIDのマッピング（マージされた行用）
    private Hashtable<Integer, Integer> responseToRequestId;

    public static PacketPairingService getInstance() {
        if (instance == null) {
            instance = new PacketPairingService();
        }
        return instance;
    }

    private PacketPairingService() {
        groupRow = new Hashtable<>();
        groupHasResponse = new HashSet<>();
        responseToRequestId = new Hashtable<>();
    }

    /**
     * すべてのペアリング情報をクリアする
     */
    public void clear() {
        groupRow.clear();
        groupHasResponse.clear();
        responseToRequestId.clear();
    }

    /**
     * グループIDに対応する行インデックスを登録する
     * @param groupId グループID
     * @param rowIndex 行インデックス
     */
    public void registerGroupRow(long groupId, int rowIndex) {
        groupRow.put(groupId, rowIndex);
    }

    /**
     * グループIDに対応する行インデックスを取得する
     * @param groupId グループID
     * @return 行インデックス、存在しない場合はnull
     */
    public Integer getRowForGroup(long groupId) {
        return groupRow.get(groupId);
    }

    /**
     * グループIDが登録されているか確認する
     * @param groupId グループID
     * @return 登録されている場合true
     */
    public boolean containsGroup(long groupId) {
        return groupRow.containsKey(groupId);
    }

    /**
     * グループにレスポンスがマージされたことを記録する
     * @param groupId グループID
     */
    public void markGroupHasResponse(long groupId) {
        groupHasResponse.add(groupId);
    }

    /**
     * グループにレスポンスがマージされているか確認する
     * @param groupId グループID
     * @return マージされている場合true
     */
    public boolean hasResponse(long groupId) {
        return groupHasResponse.contains(groupId);
    }

    /**
     * レスポンスパケットIDとリクエストパケットIDのペアリングを登録する
     * @param responsePacketId レスポンスパケットID
     * @param requestPacketId リクエストパケットID
     */
    public void registerPairing(int responsePacketId, int requestPacketId) {
        responseToRequestId.put(responsePacketId, requestPacketId);
    }

    /**
     * レスポンスパケットIDに対応するリクエストパケットIDを取得する
     * @param responsePacketId レスポンスパケットID
     * @return リクエストパケットID、存在しない場合はnull
     */
    public Integer getRequestIdForResponse(int responsePacketId) {
        return responseToRequestId.get(responsePacketId);
    }

    /**
     * レスポンスパケットIDがペアリングに登録されているか確認する
     * @param responsePacketId レスポンスパケットID
     * @return 登録されている場合true
     */
    public boolean containsResponsePairing(int responsePacketId) {
        return responseToRequestId.containsKey(responsePacketId);
    }

    /**
     * リクエストパケットIDに対応するレスポンスパケットIDを取得する
     * マージされた行の場合のみ有効
     * @param requestPacketId リクエストパケットID
     * @return レスポンスパケットID、存在しない場合は-1
     */
    public int getResponsePacketIdForRequest(int requestPacketId) {
        for (Map.Entry<Integer, Integer> entry : responseToRequestId.entrySet()) {
            if (entry.getValue() == requestPacketId) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /**
     * 選択された行がマージされた行（リクエスト+レスポンス）かどうかを判定
     * @param packetId パケットID
     * @return マージされた行の場合true
     */
    public boolean isMergedRow(int packetId) {
        return getResponsePacketIdForRequest(packetId) != -1;
    }
}

