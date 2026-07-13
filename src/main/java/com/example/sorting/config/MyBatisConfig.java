package com.example.sorting.config;

import com.example.sorting.handler.EncryptTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.jasypt.encryption.StringEncryptor;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import com.ulisesbocchio.jasyptspringboot.encryptor.DefaultLazyEncryptor;

import javax.sql.DataSource;
import java.io.IOException;

@org.springframework.context.annotation.Configuration
public class MyBatisConfig {

    @Value("${mybatis.type-aliases-package:}")
    private String typeAliasesPackage;

    @Value("${mybatis.mapper-locations:classpath:mapper/*.xml}")
    private String mapperLocations;

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource, ConfigurableEnvironment springEnv) throws Exception {
        // 初始化加密类型处理器（使用 Spring 的环境配置）
        StringEncryptor encryptor = new DefaultLazyEncryptor(springEnv);
        EncryptTypeHandler.setEncryptor(encryptor);

        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);

        // 设置 MyBatis 环境
        Environment env = new Environment("prod", new SpringManagedTransactionFactory(), dataSource);
        configuration.setEnvironment(env);

        // 设置类型别名
        if (!typeAliasesPackage.isEmpty()) {
            configuration.getTypeAliasRegistry().registerAliases(typeAliasesPackage);
        }

        // 加载 Mapper XML 文件
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(mapperLocations);
            for (Resource resource : resources) {
                XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(
                        resource.getInputStream(),
                        configuration,
                        resource.toString(),
                        configuration.getSqlFragments()
                );
                xmlMapperBuilder.parse();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load mapper locations: " + mapperLocations, e);
        }

        return new SqlSessionFactoryBuilder().build(configuration);
    }

    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
