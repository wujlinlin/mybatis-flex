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
package com.mybatisflex.core;

import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.util.MapUtil;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * MybatisFlex 的启动类
 *
 * <code>
 * MybatisFlexBootstrap.getInstance()
 * .setDatasource(...)
 * .addMapper(...)
 * .start();
 * <p>
 * <p>
 * MybatisFlexBootstrap.getInstance()
 * .execute(...)
 * </code>
 */
public class MybatisFlexBootstrap {

    protected final AtomicBoolean started = new AtomicBoolean(false);

    protected String environmentId = FlexConsts.NAME;
    protected TransactionFactory transactionFactory;

    protected FlexDataSource dataSource;
    protected Configuration configuration;
    protected List<Class<?>> mappers;

    protected SqlSessionFactory sqlSessionFactory;
    protected Class<? extends Log> logImpl;

    private final Map<Class<?>, Object> mapperObjects = new ConcurrentHashMap<>();

    /**
     * 虽然提供了 getInstance，但也允许用户进行实例化，
     * 用于创建多个 MybatisFlexBootstrap 实例达到管理多数据源的目的
     */
    public MybatisFlexBootstrap() {

    }

    private static volatile MybatisFlexBootstrap instance;

    public static MybatisFlexBootstrap getInstance() {
        if (instance == null) {
            synchronized (MybatisFlexBootstrap.class) {
                if (instance == null) {
                    instance = new MybatisFlexBootstrap();
                }
            }
        }
        return instance;
    }


    public <T> MybatisFlexBootstrap addMapper(Class<T> type) {
        if (this.mappers == null) {
            mappers = new ArrayList<>();
        }
        mappers.add(type);
        return this;
    }


    public MybatisFlexBootstrap start() {
        if (started.compareAndSet(false, true)) {
            if (dataSource == null) {
                throw new IllegalStateException("dataSource can not be null.");
            }

            //init configuration
            if (configuration == null) {

                if (transactionFactory == null) {
                    transactionFactory = new JdbcTransactionFactory();
                }

                Environment environment = new Environment(environmentId, transactionFactory, dataSource);
                configuration = new FlexConfiguration(environment);
            }

            if (logImpl != null) {
                configuration.setLogImpl(logImpl);
            }

            //init sqlSessionFactory
            this.sqlSessionFactory = new FlexSqlSessionFactoryBuilder().build(configuration);

            //init mappers
            if (mappers != null) {
                mappers.forEach(configuration::addMapper);
            }

            LogFactory.getLog(MybatisFlexBootstrap.class).debug("Mybatis-Flex has started.");
        }

        return this;
    }


    @Deprecated
    public <R, T> R execute(Class<T> mapperClass, Function<T, R> function) {
        try (SqlSession sqlSession = openSession()) {
            T mapper = sqlSession.getMapper(mapperClass);
            return function.apply(mapper);
        }
    }


    protected SqlSession openSession() {
        return sqlSessionFactory.openSession(configuration.getDefaultExecutorType(), true);
    }


    /**
     * 直接获取 mapper 对象执行
     *
     * @param mapperClass
     * @return mapperObject
     */
    public <T> T getMapper(Class<T> mapperClass) {
        Object mapperObject = MapUtil.computeIfAbsent(mapperObjects, mapperClass, clazz ->
            Proxy.newProxyInstance(mapperClass.getClassLoader()
                , new Class[]{mapperClass}
                , (proxy, method, args) -> {
                    try (SqlSession sqlSession = openSession()) {
                        T mapper1 = sqlSession.getMapper(mapperClass);
                        return method.invoke(mapper1, args);
                    }
                }));
        return (T) mapperObject;
    }


    public String getEnvironmentId() {
        return environmentId;
    }

    public MybatisFlexBootstrap setEnvironmentId(String environmentId) {
        this.environmentId = environmentId;
        return this;
    }

    public TransactionFactory getTransactionFactory() {
        return transactionFactory;
    }

    public MybatisFlexBootstrap setTransactionFactory(TransactionFactory transactionFactory) {
        this.transactionFactory = transactionFactory;
        return this;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public MybatisFlexBootstrap setDataSource(DataSource dataSource) {
        this.dataSource = new FlexDataSource(FlexConsts.NAME, dataSource);
        return this;
    }

    public MybatisFlexBootstrap setDataSource(String dataSourceKey, DataSource dataSource) {
        this.dataSource = new FlexDataSource(dataSourceKey, dataSource);
        return this;
    }

    public MybatisFlexBootstrap addDataSource(String dataSourceKey, DataSource dataSource) {
        if (this.dataSource == null) {
            this.dataSource = new FlexDataSource(dataSourceKey, dataSource);
        } else {
            this.dataSource.addDataSource(dataSourceKey, dataSource);
        }
        return this;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public MybatisFlexBootstrap setConfiguration(FlexConfiguration configuration) {
        this.configuration = configuration;
        this.environmentId = configuration.getEnvironment().getId();
        return this;
    }

    public List<Class<?>> getMappers() {
        return mappers;
    }


    public SqlSessionFactory getSqlSessionFactory() {
        return sqlSessionFactory;
    }

    public MybatisFlexBootstrap setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
        return this;
    }

    public Class<? extends Log> getLogImpl() {
        return logImpl;
    }

    public MybatisFlexBootstrap setLogImpl(Class<? extends Log> logImpl) {
        this.logImpl = logImpl;
        return this;
    }

}
