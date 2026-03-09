package com.c.config;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.elasticsearch.xpack.sql.jdbc.EsDataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * 多数据源配置类
 * 区分 MySQL 业务数据库（分库分表）与 Elasticsearch 查询引擎
 *
 * @author cyh
 * @date 2026/03/09
 */
@Configuration
public class DataSourceConfig {

    /**
     * Elasticsearch MyBatis 配置
     * 作用：处理 com.c.infrastructure.elasticsearch 包下的 Mapper 接口
     */
    @Configuration
    @MapperScan(basePackages = "com.c.infrastructure.elasticsearch", sqlSessionFactoryRef =
            "elasticsearchSqlSessionFactory")
    static class ElasticsearchMyBatisConfig {

        /**
         * 创建 Elasticsearch 数据源
         */
        @Bean("elasticsearchDataSource")
        @ConfigurationProperties(prefix = "spring.elasticsearch.datasource")
        public DataSource elasticsearchDataSource(Environment environment) {
            return new EsDataSource();
        }

        /**
         * 创建专门用于 ES 查询的 SqlSessionFactory
         */
        @Bean("elasticsearchSqlSessionFactory")
        public SqlSessionFactory elasticsearchSqlSessionFactory(@Qualifier("elasticsearchDataSource") DataSource elasticsearchDataSource) throws Exception {
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(elasticsearchDataSource);
            // 扫描 ES 专用的 XML 映射文件
            factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:/mybatis"
                    + "/mapper/elasticsearch/*.xml"));
            return factoryBean.getObject();
        }
    }

    /**
     * MySQL MyBatis 配置
     */
    @Configuration
    @MapperScan(basePackages = "com.c.infrastructure.dao", sqlSessionFactoryRef = "mysqlSqlSessionFactory")
    static class MysqlMyBatisConfig {

        @Primary
        @Bean("mysqlSqlSessionFactory")
        public SqlSessionFactory mysqlSqlSessionFactory(@Qualifier("shardingSphereDataSource") DataSource shardingSphereDataSource, @Autowired(required = false) Interceptor dbRouterDynamicMybatisPlugin) throws Exception {
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(shardingSphereDataSource);
            if (dbRouterDynamicMybatisPlugin != null) {
                factoryBean.setPlugins(dbRouterDynamicMybatisPlugin);
            }
            factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:/mybatis"
                    + "/mapper/mysql/*.xml"));
            return factoryBean.getObject();
        }

        /**
         * 显式定义事务管理器，绑定到 ShardingSphere 数据源
         */
        @Bean(name = "transactionManager")
        @Primary
        public DataSourceTransactionManager transactionManager(@Qualifier("shardingSphereDataSource") DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        /**
         * 显式定义事务模板，供 Repository 层使用
         */
        @Bean(name = "transactionTemplate")
        @Primary
        public TransactionTemplate transactionTemplate(DataSourceTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }
}