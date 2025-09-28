package com.example.dts.config;

import com.atomikos.icatch.jta.UserTransactionImp;
import com.atomikos.icatch.jta.UserTransactionManager;
import com.atomikos.jdbc.AtomikosDataSourceBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.jta.JtaTransactionManager;

import javax.sql.DataSource;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;
import java.util.Properties;

/**
 * XA 事务配置
 * 配置 Atomikos 作为 JTA 事务管理器
 * 
 * @author DTS Team
 */
@Configuration
public class XATransactionConfig {

    @Value("${spring.datasource.url}")
    private String primaryUrl;
    
    @Value("${spring.datasource.username}")
    private String primaryUsername;
    
    @Value("${spring.datasource.password}")
    private String primaryPassword;
    
    @Value("${spring.datasource.driver-class-name}")
    private String primaryDriverClassName;

    /**
     * 主数据源 - XA 数据源
     */
    @Bean(name = "primaryXADataSource")
    @Primary
    public DataSource primaryXADataSource() {
        AtomikosDataSourceBean dataSource = new AtomikosDataSourceBean();
        dataSource.setUniqueResourceName("primaryXADataSource");
        dataSource.setXaDataSourceClassName("com.mysql.cj.jdbc.MysqlXADataSource");
        
        Properties properties = new Properties();
        properties.setProperty("url", primaryUrl);
        properties.setProperty("user", primaryUsername);
        properties.setProperty("password", primaryPassword);
        properties.setProperty("pinGlobalTxToPhysicalConnection", "true");
        
        dataSource.setXaProperties(properties);
        dataSource.setMinPoolSize(5);
        dataSource.setMaxPoolSize(20);
        dataSource.setMaxLifetime(20000);
        dataSource.setBorrowConnectionTimeout(10000);
        dataSource.setLoginTimeout(10000);
        dataSource.setMaintenanceInterval(60);
        dataSource.setMaxIdleTime(60);
        dataSource.setTestQuery("SELECT 1");
        
        return dataSource;
    }

    /**
     * 辅助数据源 - XA 数据源（用于演示多数据源事务）
     */
    @Bean(name = "secondaryXADataSource")
    public DataSource secondaryXADataSource() {
        AtomikosDataSourceBean dataSource = new AtomikosDataSourceBean();
        dataSource.setUniqueResourceName("secondaryXADataSource");
        dataSource.setXaDataSourceClassName("com.mysql.cj.jdbc.MysqlXADataSource");
        
        Properties properties = new Properties();
        // 使用相同的数据库，但可以配置为不同的数据库
        properties.setProperty("url", primaryUrl);
        properties.setProperty("user", primaryUsername);
        properties.setProperty("password", primaryPassword);
        properties.setProperty("pinGlobalTxToPhysicalConnection", "true");
        
        dataSource.setXaProperties(properties);
        dataSource.setMinPoolSize(3);
        dataSource.setMaxPoolSize(10);
        dataSource.setMaxLifetime(20000);
        dataSource.setBorrowConnectionTimeout(10000);
        dataSource.setLoginTimeout(10000);
        dataSource.setMaintenanceInterval(60);
        dataSource.setMaxIdleTime(60);
        dataSource.setTestQuery("SELECT 1");
        
        return dataSource;
    }

    /**
     * JTA 用户事务
     */
    @Bean
    public UserTransaction userTransaction() throws Throwable {
        UserTransactionImp userTransactionImp = new UserTransactionImp();
        userTransactionImp.setTransactionTimeout(300);
        return userTransactionImp;
    }

    /**
     * JTA 事务管理器
     */
    @Bean(initMethod = "init", destroyMethod = "close")
    public TransactionManager atomikosTransactionManager() throws Throwable {
        UserTransactionManager userTransactionManager = new UserTransactionManager();
        userTransactionManager.setForceShutdown(false);
        return userTransactionManager;
    }

    /**
     * Spring JTA 事务管理器
     */
    @Bean
    @Primary
    public JtaTransactionManager transactionManager() throws Throwable {
        JtaTransactionManager jtaTransactionManager = new JtaTransactionManager();
        jtaTransactionManager.setTransactionManager(atomikosTransactionManager());
        jtaTransactionManager.setUserTransaction(userTransaction());
        jtaTransactionManager.setAllowCustomIsolationLevels(true);
        return jtaTransactionManager;
    }
}