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
package com.addthis.codec.binary;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.util.Stack;

import com.addthis.basis.util.Bytes;

final class BufferOut {

    ByteArrayOutputStream        out;
    Stack<ByteArrayOutputStream> stack;

    BufferOut() {
        stack = new Stack<ByteArrayOutputStream>();
        push();
    }

    public OutputStream out() {
        return out;
    }

    public void push() {
        stack.push(new ByteArrayOutputStream());
        out = stack.peek();
    }

    public void pop() throws IOException {
        ByteArrayOutputStream last = stack.pop();
        out = stack.peek();
        Bytes.writeLength(last.size(), out());
        last.writeTo(out());
    }

    @Override
    public String toString() {
        return "BufferOut:" + (out != null ? out.size() : -1);
    }
}
