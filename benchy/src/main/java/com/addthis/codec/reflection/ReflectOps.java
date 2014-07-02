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

package com.addthis.codec.reflection;


import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Test the speed of various reflection ops versus CodableInfo caching.
 */
@BenchmarkMode(Mode.Throughput) // measure as ops/ time_unit
@OutputTimeUnit(TimeUnit.MICROSECONDS) // time_unit is microseconds
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS) // how long to warm up the jvm
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS) // how many runs to average over
@Fork(1) // how many JVM forks per test; measurements are run per fork
@Threads(1) // how many threads to run concurrently; thread count is per test -- not shared
@State(Scope.Thread) // treat this enclosing class as a State object that can be used in tests
public class ReflectOps {

    /**
     * To run this benchmark, do 'mvn clean package' from the bench directory, and then either
     *
     * use the default JMH main class (it takes a regex of benchmark names):
     * 'java -jar target/microbenchmarks.jar ".*BitReversals.*"'
     *
     * call this main method instead or use the code therein to start it programmatically
     * eg. 'java -cp target/microbenchmarks.jar com.addthis.basis.util.BitReversals'
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + ReflectOps.class.getSimpleName() + ".*")
                .build();

        new Runner(opt).run();
    }

    Class<?>         classType;
    CodableClassInfo classInfo;
    CodableFieldInfo intArrayInfo;
    Class<?>         intArrayType;
    CodableFieldInfo booleanInfo;
    Class<?>         booleanType;

    @Setup(Level.Trial)
    public void makeTypeObjects() {
        classType = FieldHolder.class;
        classInfo = Fields.getClassFieldMap(classType);
        for (CodableFieldInfo fieldInfo : classInfo.values()) {
            if (fieldInfo.getName().equals("intArrayField")) {
                intArrayInfo = fieldInfo;
                intArrayType = fieldInfo.getType();
            } else {
                booleanInfo = fieldInfo;
                booleanType = fieldInfo.getType();
            }
        }
    }

    static class FieldHolder {
        public int[]   intArrayField;
        public boolean booleanField;
    }

    @Benchmark
    public boolean reflectCheckArray() {
        boolean a = intArrayType.isArray();
        boolean b = booleanType.isArray();
        return a && b;
    }

    @Benchmark
    public boolean infoCheckArray() {
        boolean a = intArrayInfo.isArray();
        boolean b = booleanInfo.isArray();
        return a && b;
    }
}
