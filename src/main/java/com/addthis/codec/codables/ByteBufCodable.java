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
package com.addthis.codec.codables;

import io.netty.buffer.ByteBuf;

/**
 * for classes that want to handle their own direct serialization and prefer to not
 * use and/or allocate byte arrays
 */
public interface ByteBufCodable extends Codable {

    public void writeBytes(ByteBuf buf);

    public void readBytes(ByteBuf buf);
}
