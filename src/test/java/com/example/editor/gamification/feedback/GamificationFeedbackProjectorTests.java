package com.example.editor.gamification.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.gamification.*;

class GamificationFeedbackProjectorTests {
    @Test void projectsPointsAchievementChallengeLevelAndRewardDeterministically(){
        var definitions=definitions(Map.of()); var bindings=bindings(Map.of());
        var ledger=new GamificationLedger("1.0","scope","scope",Instant.now(),List.of(entry("one",5,"achievement:first","challenge:quality","reward:direct",GamificationLedgerStatus.ACTIVE,"")));
        var feedback=GamificationFeedbackProjector.project(definitions,bindings,ledger,"actor",100);
        assertThat(feedback).extracting(UserFeedbackEnvelope::kind).contains(UserFeedbackKind.POINTS_AWARDED,UserFeedbackKind.ACHIEVEMENT_UNLOCKED,UserFeedbackKind.CHALLENGE_PROGRESS,UserFeedbackKind.REWARD_UNLOCKED,UserFeedbackKind.LEVEL_REACHED);
        assertThat(feedback).extracting(UserFeedbackEnvelope::id).doesNotHaveDuplicates();
        assertThat(feedback).allMatch(item->item.presentation().enabled());
    }
    @Test void penaltyUsesWarningAndNeverDropsBelowProjectionContract(){
        var ledger=new GamificationLedger("1.0","scope","scope",Instant.now(),List.of(entry("penalty",-3,"","","",GamificationLedgerStatus.ACTIVE,"")));
        var feedback=GamificationFeedbackProjector.project(definitions(Map.of()),bindings(Map.of()),ledger,"actor",100);
        assertThat(feedback).anySatisfy(item->{assertThat(item.kind()).isEqualTo(UserFeedbackKind.PENALTY_APPLIED);assertThat(item.severity()).isEqualTo(UserFeedbackSeverity.WARNING);assertThat(item.pointsDelta()).isEqualTo(-3);});
    }
    @Test void rejectedEntriesAreSilentByDefaultAndConfigurable(){
        var rejected=new GamificationLedger("1.0","scope","scope",Instant.now(),List.of(entry("rejected",0,"","","",GamificationLedgerStatus.REJECTED,"")));
        assertThat(GamificationFeedbackProjector.project(definitions(Map.of()),bindings(Map.of()),rejected,"actor",100)).isEmpty();
        assertThat(GamificationFeedbackProjector.project(definitions(Map.of("hydra.feedback.includeRejected","true")),bindings(Map.of()),rejected,"actor",100)).singleElement().extracting(UserFeedbackEnvelope::kind).isEqualTo(UserFeedbackKind.APPLICATION_REJECTED);
    }
    @Test void compensationsAreVisibleAndStable(){
        var ledger=new GamificationLedger("1.0","scope","scope",Instant.now(),List.of(entry("undo",-5,"","","",GamificationLedgerStatus.ACTIVE,"ledger:original")));
        var first=GamificationFeedbackProjector.project(definitions(Map.of()),bindings(Map.of()),ledger,"actor",100);
        var second=GamificationFeedbackProjector.project(definitions(Map.of()),bindings(Map.of()),ledger,"actor",100);
        assertThat(first).singleElement().extracting(UserFeedbackEnvelope::kind).isEqualTo(UserFeedbackKind.AWARD_COMPENSATED);
        assertThat(second).extracting(UserFeedbackEnvelope::id).containsExactlyElementsOf(first.stream().map(UserFeedbackEnvelope::id).toList());
    }
    @Test void corruptPolicyAnnotationsFailClosedToDefaults(){
        var policy=GamificationFeedbackPolicyResolver.policy(definitions(Map.of("hydra.feedback.durationMillis","broken","hydra.feedback.surfaces","garbage","hydra.feedback.maxVisible","-99")),bindings(Map.of()),"rule:quality");
        assertThat(policy.durationMillis()).isEqualTo(4000);assertThat(policy.surfaces()).contains(UserFeedbackSurface.TOAST,UserFeedbackSurface.ACTIVITY);assertThat(policy.maxVisible()).isEqualTo(1);
    }
    @Test void numericPolicyBoundsAreClampedAndMalformedValuesUseDefaults(){
        var policy=GamificationFeedbackPolicyResolver.policy(definitions(Map.of(
                "hydra.feedback.durationMillis","999999",
                "hydra.feedback.maxVisible","999",
                "hydra.feedback.dedupeWindowSeconds","-7",
                "hydra.feedback.aggregateWindowMillis","broken")),bindings(Map.of()),"rule:quality");
        assertThat(policy.durationMillis()).isEqualTo(30000);
        assertThat(policy.maxVisible()).isEqualTo(20);
        assertThat(policy.dedupeWindowSeconds()).isZero();
        assertThat(policy.aggregateWindowMillis()).isEqualTo(750);
    }
    @Test void projectsTenThousandEntriesWithBoundedOutput(){
        List<GamificationLedgerEntry> entries=new ArrayList<>();for(int i=0;i<10_000;i++)entries.add(entry("bulk-"+i,1,"","","",GamificationLedgerStatus.ACTIVE,""));
        var feed=GamificationFeedbackProjector.project(definitions(Map.of()),bindings(Map.of()),new GamificationLedger("1.0","scope","scope",Instant.now(),entries),"actor",137);
        assertThat(feed).hasSize(137);assertThat(feed).extracting(UserFeedbackEnvelope::id).doesNotHaveDuplicates();
    }
    private static GamificationDefinitionModel definitions(Map<String,String> annotations){return new GamificationDefinitionModel("1.0","definitions","scope","Game",List.of(new PointTypeDefinition("points:quality","Quality","",-20)),List.of(new LevelDefinition("level:one","Level one","points:quality",5,"reward:level")),List.of(new AchievementDefinition("achievement:first","First","First achievement","reward:achievement",false)),List.of(new ChallengeDefinition("challenge:quality","Challenge","Do work",2,"reward:challenge")),List.of(new RewardDefinition("reward:direct","Direct","Direct reward",Map.of()),new RewardDefinition("reward:level","Level reward","",Map.of()),new RewardDefinition("reward:achievement","Achievement reward","",Map.of()),new RewardDefinition("reward:challenge","Challenge reward","",Map.of())),annotations);}
    private static GamificationBindingModel bindings(Map<String,String> annotations){return new GamificationBindingModel("1.0","bindings","scope","definitions",List.of(new GamificationRule("rule:quality","Quality rule","event:test","","EQUALS",null,"points:quality",5,"achievement:first","challenge:quality","reward:direct",GamificationRepeatPolicy.UNLIMITED,0,0,0,GamificationEffectKind.AWARD,true)),annotations);}
    private static GamificationLedgerEntry entry(String id,long points,String achievement,String challenge,String reward,GamificationLedgerStatus status,String compensation){return new GamificationLedgerEntry("ledger:"+id,"scope","actor","event:"+id,"rule:quality",Instant.parse("2026-09-19T12:00:00Z").plusSeconds(Math.abs(id.hashCode()%1000)),"points:quality",points,achievement,challenge,reward,status,compensation,"correlation",Map.of("reason","test"));}
}
