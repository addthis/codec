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
package com.addthis.codec.json;

import com.addthis.maljson.JSONException;
import com.addthis.maljson.JSONObject;

public class CodableJSONObject extends JSONObject implements JSONCodable {

    public void fromJSONObject(JSONObject jo) throws Exception {
        mergeFrom(jo);
    }

    public JSONObject toJSONObject() throws Exception {
        return this;
    }

    /**
     * Merges the contents of the 'from' object into this object.
     */
    public JSONObject mergeFrom(JSONObject from) throws JSONException {
        if (from == null) {
            return this;
        }
        String keys[] = new String[length()];
        keys = from.keySet().toArray(keys);
        for (String key : keys) {
            if (key == null || key.length() == 0) {
                continue;
            }
            Object ov = opt(key);
            Object nv = from.get(key);
            if (nv == null || nv == JSONObject.NULL) {
                remove(key);
            } else if (ov instanceof JSONObject && nv instanceof JSONObject) {
                ((CodableJSONObject) ov).mergeFrom((JSONObject) nv);
            } else {
                put(key, nv);
            }
        }
        return this;
    }
}
