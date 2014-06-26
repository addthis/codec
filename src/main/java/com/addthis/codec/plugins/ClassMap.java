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
package com.addthis.codec.plugins;

import java.util.Iterator;
import java.util.Set;

import com.addthis.codec.Codec;
import com.addthis.codec.annotations.ArraySugar;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* A bi-directional map between Strings and Classes. */
public class ClassMap {

    private static final Logger log = LoggerFactory.getLogger(ClassMap.class);

    private BiMap<String, Class<?>> map = HashBiMap.create();
    private Class<?> arraySugar;

    public String getClassField() {
        return "class";
    }

    public Class<?> setArraySugar(Class<?> newArraySugar) {
        Class<?> prev = this.arraySugar;
        if (prev != null) {
            log.warn("warning: overriding class map array sugar class {} with old type {} " +
                           "and new type {}", prev, prev, newArraySugar);
        }
        this.arraySugar = newArraySugar;
        return prev;
    }

    public Class<?> getArraySugar() {
        return arraySugar;
    }

    public String getCategory() {
        return null;
    }

    public ClassMap misnomerMap() {
        return null;
    }

    public ClassMap add(Class<?> type) {
        return add(type.getSimpleName(), type);
    }

    public Set<String> getNames() {
        return map.keySet();
    }

    public ClassMap add(String name, Class<?> type) {
        Class prev = map.put(name, type);
        if (prev != null) {
            log.warn("warning: overriding class map for key "
                     + name + " with old type " + prev + " and new type " + type);
        }
        if (type.getAnnotation(ArraySugar.class) != null) {
            setArraySugar(type);
        }
        return this;
    }

    public ClassMap remove(Class<?> type) {
        map.inverse().remove(type);
        return this;
    }

    public ClassMap remove(String name, Class<?> type) {
        map.remove(name);
        return this;
    }

    public boolean contains(String name) {
        return map.containsKey(name);
    }

    public boolean contains(Class<?> type) {
        return map.containsValue(type);
    }

    public String getClassName(Class<?> type) {
        String alt = map.inverse().get(type);
        return alt != null ? alt : type.getName();
    }

    public Class<?> getClass(String type) throws Exception {
        Class<?> alt = map.get(type);
        if (alt != null) {
            return alt;
        }
        try {
            Class theClass = Class.forName(type);
            return (theClass);
        } catch (ClassNotFoundException ex) {
            throw classNameSuggestions(type, ex);
        }
    }

    private Exception classNameSuggestions(String type, ClassNotFoundException ex) {
        Set<String> classNames = this.getNames();
        String category = this.getCategory();
        ClassMap misnomerMap = this.misnomerMap();
        if (classNames.isEmpty()) {
            return ex;
        }
        StringBuilder builder = new StringBuilder();

        if (category != null) {
            builder.append("Could not instantiate an instance of the ");
            builder.append(category);
            builder.append(" category that you have specified");
        } else {
            builder.append("Could not instantiate something you have specified");
        }
        builder.append(" with \"");
        builder.append(type);
        builder.append("\".");
        if (misnomerMap != null && misnomerMap.getCategory() != null && misnomerMap.contains(type)) {
            builder.append("\nIt looks like you tried to instantiate a ");
            builder.append(misnomerMap.getCategory());
            builder.append(" and I am expecting a ");
            builder.append(category);
            builder.append(".");
        } else {
            builder.append("\nPerhaps you intended one of the following: ");
            Iterator<String> iterator = classNames.iterator();
            while (iterator.hasNext()) {
                String name = iterator.next();

                if (!iterator.hasNext() && classNames.size() > 1) {
                    builder.append("or ");
                }

                builder.append('"');
                builder.append(name);
                builder.append('"');
                builder.append(" ");
            }
            builder.append("?\n");
        }
        return new Exception(builder.toString(), ex);
    }
}
