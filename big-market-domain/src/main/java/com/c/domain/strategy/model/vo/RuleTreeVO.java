package com.c.domain.strategy.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 规则树根对象 VO
 * 该类是决策树配置的顶层容器，封装了树的基本信息、执行入口以及全量节点数据。
 *
 * @author cyh
 * @date 2026/01/19
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor // 必须有无参构造函数，否则反序列化会失败
public class RuleTreeVO implements Serializable {
    private static final long serialVersionUID = -1L;

    private String treeId;
    private String treeName;
    private String treeDesc;
    private String treeRootRuleNode;

    // 检查点：确保属性名是 treeNodeMap，且类型是 Map
    private Map<String, RuleTreeNodeVO> treeNodeMap;
}