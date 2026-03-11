package com.c.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-Job 分布式任务调度执行器配置
 *
 * @author cyh
 * @date 2026/03/10
 */
@Slf4j
@Configuration
public class XxlJobAutoConfig {

    @Value("${xxl.job.admin.addresses}")
    // 调度中心部署地址：如 "http://address:port/xxl-job-admin"
    private String adminAddresses;

    @Value("${xxl.job.accessToken}")
    // 执行器通讯令牌：需与调度中心配置保持一致
    private String accessToken;

    @Value("${xxl.job.executor.appname}")
    // 执行器 AppName：调度中心进行任务发现的分组依据
    private String appname;

    @Value("${xxl.job.executor.address}")
    // 执行器注册地址：为空则由框架自动获取 IP:PORT
    private String address;

    @Value("${xxl.job.executor.ip}")
    // 执行器 IP：在多网卡或 Docker 容器环境下建议手动指定
    private String ip;

    @Value("${xxl.job.executor.port}")
    // 执行器端口：默认 9999（若设为 0 则自动找可用端口）
    private int port;

    @Value("${xxl.job.executor.logpath}")
    // 执行器运行日志存储路径
    private String logPath;

    @Value("${xxl.job.executor.logretentiondays}")
    // 日志保留天数：过期自动清理（需 >= 3 生效）
    private int logRetentionDays;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info(">>>>>>>>>>> xxl-job config init. appname: {}", appname);

        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        xxlJobSpringExecutor.setAppname(appname);
        xxlJobSpringExecutor.setAddress(address);
        xxlJobSpringExecutor.setIp(ip);
        xxlJobSpringExecutor.setPort(port);
        xxlJobSpringExecutor.setAccessToken(accessToken);
        xxlJobSpringExecutor.setLogPath(logPath);
        xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);

        return xxlJobSpringExecutor;
    }

    /* -----------------------------------------------------------------------------------
     * Docker 环境部署注意点：
     * 1. 网络隔离：如果执行器在容器内，xxl.job.executor.ip 建议配置为宿主机 IP。
     * 2. 端口映射：Docker 启动时需映射执行器端口（如 9999:9999），确保调度中心能反向访问。
     * ----------------------------------------------------------------------------------- */

    /* -----------------------------------------------------------------------------------
     * 针对多网卡、容器内部署等情况，可借助 "spring-cloud-commons" 提供的 "InetUtils" 组件灵活定制注册IP；
     *      1、引入依赖：
     *          <dependency>
     *             <groupId>org.springframework.cloud</groupId>
     *             <artifactId>spring-cloud-commons</artifactId>
     *             <version>${version}</version>
     *         </dependency>
     *      2、配置文件，或者容器启动变量
     *          spring.cloud.inetutils.preferred-networks: 'xxx.xxx.xxx.'
     *      3、获取IP
     *          String ip_ = inetUtils.findFirstNonLoopbackHostInfo().getIpAddress();
     * ----------------------------------------------------------------------------------- */

}