/*
 *  Copyright (c) 2022-2023, Mybatis-Flex (fuhai999@gmail.com).
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.mybatisflex.core.datasource;

import java.util.function.Supplier;

public class DataSourceKey {

    private DataSourceKey() {
    }

    private static final ThreadLocal<String> keyThreadLocal = new ThreadLocal<>();

    public static void use(String dataSourceKey) {
        keyThreadLocal.set(dataSourceKey);
    }

    public static <T> T use(String dataSourceKey, Supplier<T> supplier) {
        try {
            use(dataSourceKey);
            return supplier.get();
        } finally {
            clear();
        }
    }

    public static void use(String dataSourceKey, Runnable runnable) {
        try {
            use(dataSourceKey);
            runnable.run();
        } finally {
            clear();
        }
    }

    public static void clear() {
        keyThreadLocal.remove();
    }

    public static String get() {
        return keyThreadLocal.get();
    }

}
