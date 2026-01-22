package com.c.domain.strategy.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 规则树节点 VO
 * 每一个节点代表决策树中的一个逻辑判断点，包含了具体的业务规则属性及其下挂的连线关系。
 *
 * @author cyh
 * @date 2026/01/19
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor // 必须有
public class RuleTreeNodeVO implements Serializable {
    private static final long serialVersionUID = -1L;

    private String treeId;
    private String ruleKey;
    private String ruleDesc;
    private String ruleValue;
    private List<RuleTreeNodeLineVO> treeNodeLineVOList;
}