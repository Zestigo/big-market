package com.c.domain.strategy.model.vo;

import com.c.domain.strategy.service.rule.filter.factory.DefaultLogicFactory;
import com.c.types.common.Constants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 策略奖项规则模型VO
 *
 * @author cyh
 * @date 2026/01/18
 */
@Getter
// value值对象，不需要设置值
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyAwardRuleModelVO {
    private String ruleModels;

        public String[] raffleCenterRuleModelList() {
            List<String> ruleModelList = new ArrayList<>();
            for (String ruleModel : ruleModels.split(Constants.SPLIT)) {
                if (DefaultLogicFactory.LogicModel.isCenter(ruleModel)) ruleModelList.add(ruleModel);
            }
            return ruleModelList.toArray(new String[0]);
        }

        public String[] raffleAfterRuleModelList() {
            List<String> ruleModelList = new ArrayList<>();
        for (String ruleModel : ruleModels.split(Constants.SPLIT)) {
            if (DefaultLogicFactory.LogicModel.isAfter(ruleModel)) ruleModelList.add(ruleModel);
        }
        return ruleModelList.toArray(new String[0]);
    }
}
