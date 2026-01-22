package com.c.domain.strategy.service.rule.filter.factory;

import com.c.domain.strategy.model.entity.RuleActionEntity;
import com.c.domain.strategy.service.annotation.LogicStrategy;
import com.c.domain.strategy.service.rule.filter.ILogicFilter;
import com.c.types.common.Constants;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抽奖策略规则过滤器工厂类
 * 核心作用：统一管理所有抽奖规则过滤器（如黑名单过滤、权重过滤），提供过滤器的获取入口
 * 设计模式：简单工厂模式 + 注解驱动 + 依赖注入
 */
@Service
public class DefaultLogicFactory {

    /**
     * 规则过滤器缓存Map（线程安全）
     * Key：规则编码（如rule_blacklist、rule_weight）
     * Value：对应的规则过滤器实例（ILogicFilter接口实现类）
     * 选用ConcurrentHashMap原因：抽奖场景为高并发场景，保证多线程下Map操作的线程安全
     */
    public Map<String, ILogicFilter<?>> logicFilterMap = new ConcurrentHashMap<>();

    /**
     * 构造方法（Spring自动注入所有ILogicFilter实现类）
     * 核心逻辑：扫描所有规则过滤器，通过注解绑定规则编码，初始化过滤器缓存Map
     *
     * @param logicFilters Spring容器中所有实现了ILogicFilter接口的实例列表（自动注入）
     */
    public DefaultLogicFactory(List<ILogicFilter<?>> logicFilters) {
        // 遍历所有过滤器实例，完成缓存初始化
        logicFilters.forEach(logic -> {
            // 1. 获取当前过滤器类上的@LogicStrategy注解（Spring注解工具类，高效获取注解）
            LogicStrategy strategy = AnnotationUtils.findAnnotation(logic.getClass(), LogicStrategy.class);
            // 2. 仅当注解存在时，将过滤器存入缓存Map
            if (null != strategy) {
                // 从注解中获取规则枚举，再拿到枚举的编码作为Map的Key
                logicFilterMap.put(strategy.logicMode().getCode(), logic);
            }
        });
    }

    /**
     * 对外暴露规则过滤器缓存的方法（泛型方法）
     * 作用：给业务层提供统一的过滤器获取入口，限定过滤器处理的实体类型
     *
     * @param <T> 泛型约束：T必须是RuleActionEntity.RaffleEntity（抽奖核心实体）的子类
     * @return 类型适配后的过滤器Map，业务层可直接根据规则编码获取对应过滤器
     */
    public <T extends RuleActionEntity.RaffleEntity> Map<String, ILogicFilter<T>> openLogicFilter() {
        // 类型转换说明：
        // 1. 先转成Map<?, ?>（无限制通配符）避免Java编译的"未检查转换"警告
        // 2. 再转成目标泛型Map<String, ILogicFilter<T>>，业务层使用时T符合约束，转换安全
        return (Map<String, ILogicFilter<T>>) (Map<?, ?>) logicFilterMap;
    }

    /**
     * 内部枚举类：抽奖规则类型枚举（规范规则编码，避免硬编码）
     * 每个枚举值对应一种抽奖规则，包含「规则编码」和「规则说明」
     */
    @Getter // Lombok：自动生成code和info的getter方法
    @AllArgsConstructor // Lombok：自动生成全参构造方法（给code和info赋值）
    public enum LogicModel {

        /**
         * 权重规则：抽奖前根据奖品权重，筛选出当前用户可抽奖的奖品范围KEY
         */
        RULE_WEIGHT(Constants.RULE_WEIGHT, "【抽奖前规则】根据抽奖权重返回可抽奖范围KEY", "before"),

        /**
         * 黑名单规则：抽奖前过滤黑名单用户，命中黑名单则直接返回不中奖
         */
        RULE_BLACKLIST(Constants.RULE_BLACKLIST, "【抽奖前规则】黑名单规则过滤，命中黑名单则直接返回", "before"),

        /**
         * 黑名单规则：抽奖前过滤黑名单用户，命中黑名单则直接返回不中奖
         */
        RULE_LOCK(Constants.RULE_LOCK, "【抽奖中规则】抽奖 n 次后，对应奖品可解锁抽奖", "center"), RULE_LOCK_AWARD(Constants.RULE_LOCK_AWARD,
                "【抽奖后规则】幸运奖兜底奖品", "after"),
        ;

        /** 规则唯一编码（作为过滤器Map的Key） */
        private final String code;
        /** 规则说明（自注释，提升代码可读性） */
        private final String info;
        /** 规则类型 */
        private final String type;

        // static!!!
        public static boolean isCenter(String ruleModel) {
            return "center".equals(LogicModel.valueOf(ruleModel.toUpperCase()).type);
        }
        public static boolean isAfter(String ruleModel) {
            return "after".equals(LogicModel.valueOf(ruleModel.toUpperCase()).type);
        }
    }
}


