package com.example.editor.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.event.ReservedEventCatalog;

class GamificationPenaltyTests {
    private final GamificationChecker checker = new GamificationChecker();

    @Test void positivePenaltyAmountIsStoredAsNegativeDelta() { assertThat(penalty(3,"","","").points()).isEqualTo(-3); }
    @Test void negativeAwardAmountIsStoredAsPositiveDelta() { assertThat(rule(-4,GamificationEffectKind.AWARD,"","","").points()).isEqualTo(4); }
    @Test void explicitPenaltyKindNormalizesPositiveAmountToNegativeDelta() { assertThat(new GamificationRule("rule:penalty","Penalty","core.validation.execution-completed","","EQUALS",null,"points:quality",2,"","","",GamificationRepeatPolicy.ONCE_PER_EVENT,0,5,5,GamificationEffectKind.PENALTY,true).points()).isEqualTo(-2); }
    @Test void zeroMinimumBalancePreventsNegativeBalance() { assertThat(new PointTypeDefinition("points:quality", "Quality", "",0L).minimumBalance()).isZero(); }
    @Test void pointTypeCanAllowBoundedDebt() { assertThat(new PointTypeDefinition("points:quality", "Quality", "",-20).minimumBalance()).isEqualTo(-20); }
    @Test void acceptsPurePointPenalty() { assertThat(checker.check(definitions(),bindings(penalty(2,"","","")),null).valid()).isTrue(); }
    @Test void rejectsPenaltyThatGrantsAchievement() { assertCode(penalty(2,"achievement:a","",""),"PENALTY_CANNOT_GRANT_REWARD"); }
    @Test void rejectsPenaltyThatAdvancesChallenge() { assertCode(penalty(2,"","challenge:a",""),"PENALTY_CANNOT_GRANT_REWARD"); }
    @Test void rejectsPenaltyThatGrantsReward() { assertCode(penalty(2,"","","reward:a"),"PENALTY_CANNOT_GRANT_REWARD"); }
    @Test void penaltyEventIsReserved() { assertThat(ReservedEventCatalog.eventTypeIds()).contains("core.gamification.penalty-applied"); }
    @Test void awardKeepsRewardReferences() { assertThat(rule(2,GamificationEffectKind.AWARD,"achievement:a","challenge:a","reward:a").rewardId()).isEqualTo("reward:a"); }
    @Test void penaltyRuleKeepsExplicitEffectKind() { assertThat(penalty(2,"","","").effectKind()).isEqualTo(GamificationEffectKind.PENALTY); }

    private void assertCode(GamificationRule rule,String code){assertThat(checker.check(definitions(),bindings(rule),null).diagnostics()).anyMatch(item->item.code().equals(code));}
    private GamificationRule penalty(long amount,String achievement,String challenge,String reward){return rule(amount,GamificationEffectKind.PENALTY,achievement,challenge,reward);}
    private GamificationRule rule(long points,GamificationEffectKind effect,String achievement,String challenge,String reward){return new GamificationRule("rule:test","Rule","core.validation.execution-completed","","EQUALS",null,"points:quality",points,achievement,challenge,reward,GamificationRepeatPolicy.ONCE_PER_EVENT,0,10,10,effect,true);}
    private GamificationDefinitionModel definitions(){return new GamificationDefinitionModel("1.0","gamification-definitions","metamodel","Gamification",List.of(new PointTypeDefinition("points:quality", "Quality", "",-20)),List.of(),List.of(new AchievementDefinition("achievement:a","A","","",false)),List.of(new ChallengeDefinition("challenge:a","A","",1,"")),List.of(new RewardDefinition("reward:a","A","",Map.of())),Map.of());}
    private GamificationBindingModel bindings(GamificationRule rule){return new GamificationBindingModel("1.0","gamification-bindings","metamodel","gamification-definitions",List.of(rule),Map.of());}
}
