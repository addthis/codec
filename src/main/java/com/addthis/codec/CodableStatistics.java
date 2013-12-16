/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.addthis.codec;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class CodableStatistics {

    /**
     * Contains the data as it is collected.
     */
    private final Map<String, Long> data;

    /**
     * Contains data regarding fields that are maps.
     */
    private final Map<String, Map<Object, Long>> mapData;

    private long totalSize;

    /**
     * Immutable maps for export.
     */
    private Map<String, Long> statistics;
    private Map<String, Map<Object, Long>> mapStatistics;


    CodableStatistics() {
        this.data = new HashMap<String, Long>();
        this.mapData = new HashMap<String, Map<Object, Long>>();
    }

    void export() {
        this.statistics = Collections.unmodifiableMap(data);

        Map<String, Map<Object, Long>> exportMapStatistics = new HashMap<String, Map<Object, Long>>();

        for (Map.Entry<String, Map<Object, Long>> entry : mapData.entrySet()) {
            exportMapStatistics.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }

        this.mapStatistics = Collections.unmodifiableMap(exportMapStatistics);
    }

    Map<String, Long> getData() {
        return data;
    }

    Map<String, Map<Object, Long>> getMapData() {
        return mapData;
    }

    void setTotalSize(long size) {
        totalSize = size;
    }

    public Map<String, Long> getStatistics() {
        return statistics;
    }

    public Map<String, Map<Object, Long>> getMapStatistics() {
        return mapStatistics;
    }

    public long getTotalSize() {
        return totalSize;
    }
}
