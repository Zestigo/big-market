package com.c.domain.strategy.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@NoArgsConstructor
public class RuleTreeNodeVO {

    /** 规则树ID：标识该节点所属的决策树 */
    private Integer treeId;

    /** 规则Key：用于定位具体的逻辑处理器（如：user_score_check） */
    private String ruleKey;

    /** 规则描述：方便开发人员理解该节点用途（如：用户积分校验） */
    private String ruleDesc;

     /** 规则比值：该节点预设的阈值或参数值（如：校验用户积分是否达到 100） */
    private String ruleValue;

    /** 规则连线集合：从当前节点出发的所有可能走向（包含了判断条件和目标节点） */
    private List<RuleTreeNodeLineVO> treeNodeLineVOList;

}