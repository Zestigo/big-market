package com.c.domain.strategy.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则决策树节点值对象
 * * 职责：描述决策树中的逻辑判断原子。每个节点代表一个具体的规则判定点（如次数校验、库存校验）。
 * 作用：通过 ruleKey 关联具体的执行逻辑，并根据执行结果沿 treeNodeLineVOList 向下寻找下一跳节点。
 *
 * @author cyh
 * @date 2026/01/19
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RuleTreeNodeVO implements Serializable {

    private static final long serialVersionUID = -7643500331611028791L;

    /** 规则树 ID：归属于哪棵决策树 */
    private String treeId;

    /** 规则键值：对应具体规则执行器的唯一标识（如：rule_lock, rule_luck_award） */
    private String ruleKey;

    /** 规则描述：用于业务识别与后台展示 */
    private String ruleDesc;

    /** 规则比对值：规则执行所需的配置参数（如：锁定的抽奖次数阈值） */
    private String ruleValue;

    /** 节点连线集合：描述当前节点在不同判定结果（ALLOW/TAKE_OVER）下的流向关系 */
    private List<RuleTreeNodeLineVO> treeNodeLineVOList;

    /**
     * 安全获取连线列表
     * 避免在规则引擎递归遍历时产生空指针异常。
     */
    public List<RuleTreeNodeLineVO> getTreeNodeLineVOList() {
        return treeNodeLineVOList == null ? new ArrayList<>() : treeNodeLineVOList;
    }

}