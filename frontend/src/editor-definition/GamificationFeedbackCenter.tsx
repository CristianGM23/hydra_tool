import { useEffect, useMemo, useRef, useState } from 'react';
import { HydraButton } from '../design-system/HydraButton';
import { useHydraLanguage } from '../i18n';
import { loadGamificationFeedback } from '../gamification-designer/api';
import type { UserFeedbackEnvelope } from '../gamification-designer/types';

const BASE_REFRESH_MS=1800;
const MAX_BACKOFF_MS=30000;

export function GamificationFeedbackCenter({semanticScopeId,enabled}:{semanticScopeId:string;enabled:boolean}){
    if(!enabled)return null;
    return <GamificationFeedbackSession key={semanticScopeId} semanticScopeId={semanticScopeId}/>;
}

function GamificationFeedbackSession({semanticScopeId}:{semanticScopeId:string}){
    const {t}=useHydraLanguage();
    const [items,setItems]=useState<UserFeedbackEnvelope[]>([]);
    const [toasts,setToasts]=useState<UserFeedbackEnvelope[]>([]);
    const [activityOpen,setActivityOpen]=useState(false);
    const seen=useRef(new Set<string>());
    const signatures=useRef(new Map<string,number>());
    const initialized=useRef(false);
    useEffect(()=>{
        if(semanticScopeId==='')return;
        let disposed=false;let timer:number|undefined;let failures=0;
        const schedule=(delay:number)=>{if(!disposed)timer=window.setTimeout(()=>void refresh(),delay);};
        const refresh=async()=>{
            if(disposed)return;
            if(typeof document!=='undefined'&&document.visibilityState==='hidden'){schedule(Math.min(10000,BASE_REFRESH_MS*3));return;}
            try{
                const loaded=await loadGamificationFeedback(semanticScopeId,'',200);if(disposed)return;
                failures=0;const now=Date.now();const fresh=loaded.filter(item=>!seen.current.has(item.id));loaded.forEach(item=>seen.current.add(item.id));setItems(loaded);
                const eligible=initialized.current?fresh:fresh.filter(item=>now-new Date(item.occurredAt).getTime()<=Math.max(10000,(item.presentation?.durationMillis??4000)*2));initialized.current=true;
                for(const item of aggregateFeedback(eligible,t)){
                    if(item.presentation?.enabled===false||!(item.presentation?.surfaces??[]).includes('TOAST'))continue;
                    const signature=[item.kind,item.pointTypeId,item.achievementId,item.challengeId,item.rewardId,item.levelId,item.title].join('|');
                    const previous=signatures.current.get(signature)??0;const dedupeMs=Math.max(0,(item.presentation.dedupeWindowSeconds??0)*1000);
                    if(dedupeMs>0&&now-previous<dedupeMs)continue;signatures.current.set(signature,now);
                    setToasts(current=>[item,...current.filter(existing=>existing.id!==item.id)].slice(0,Math.max(1,item.presentation.maxVisible??3)));
                    window.setTimeout(()=>setToasts(current=>current.filter(existing=>existing.id!==item.id)),Math.max(1000,item.presentation.durationMillis??4000));
                }
                if(signatures.current.size>512){const cutoff=now-3600000;for(const [key,value] of signatures.current)if(value<cutoff)signatures.current.delete(key);}
                schedule(BASE_REFRESH_MS);
            }catch{
                failures=Math.min(failures+1,6);schedule(Math.min(MAX_BACKOFF_MS,BASE_REFRESH_MS*(2**failures)));
            }
        };
        const onVisibility=()=>{if(typeof document!=='undefined'&&document.visibilityState==='visible'){if(timer!==undefined)window.clearTimeout(timer);schedule(50);}};
        if(typeof document!=='undefined')document.addEventListener('visibilitychange',onVisibility);
        void refresh();
        return()=>{disposed=true;if(timer!==undefined)window.clearTimeout(timer);if(typeof document!=='undefined')document.removeEventListener('visibilitychange',onVisibility);};
    },[semanticScopeId,t]);
    const activity=useMemo(()=>items.filter(item=>item.presentation.persistActivity),[items]);
    return <><section className="hydra-feedback-toasts" aria-label={t('Gamification feedback')}>{toasts.map(item=><article key={item.id} className={`hydra-feedback-toast severity-${item.severity.toLowerCase()}`}><header><strong>{item.title}</strong><HydraButton className="compact" type="button" aria-label={t('Dismiss feedback')} onClick={()=>setToasts(current=>current.filter(existing=>existing.id!==item.id))}>×</HydraButton></header>{item.message&&<p>{item.message}</p>}{item.presentation.showPoints&&item.pointsDelta!==0&&<b>{item.pointsDelta>0?'+':''}{item.pointsDelta}</b>}{item.presentation.showProgress&&item.progressTarget>0&&<progress value={Math.min(item.progressCurrent,item.progressTarget)} max={item.progressTarget}/>} {item.nextStep&&<small>{item.nextStep}</small>}</article>)}</section><HydraButton type="button" className="hydra-feedback-activity-toggle" onClick={()=>setActivityOpen(value=>!value)}>{t('Activity')} {activity.length}</HydraButton>{activityOpen&&<aside className="hydra-feedback-activity" aria-label={t('Gamification activity')}><header><strong>{t('Activity')}</strong><HydraButton type="button" className="compact" onClick={()=>setActivityOpen(false)}>×</HydraButton></header>{activity.map(item=><article key={`activity:${item.id}`} className={`severity-${item.severity.toLowerCase()}`}><strong>{item.title}</strong><span>{item.message}</span>{item.nextStep&&<small>{item.nextStep}</small>}</article>)}</aside>}</>;
}

function aggregateFeedback(items:UserFeedbackEnvelope[],t:(key:string)=>string):UserFeedbackEnvelope[]{
    if(items.length<2)return items;
    const groups=new Map<string,UserFeedbackEnvelope[]>();
    for(const item of items){const key=[item.kind,item.pointTypeId,item.achievementId,item.challengeId,item.rewardId,item.levelId].join('|');const group=groups.get(key)??[];group.push(item);groups.set(key,group);}
    const result:UserFeedbackEnvelope[]=[];
    for(const group of groups.values()){
        group.sort((a,b)=>new Date(a.occurredAt).getTime()-new Date(b.occurredAt).getTime());let pending:UserFeedbackEnvelope[]=[];
        const flush=()=>{if(pending.length===0)return;if(pending.length===1){result.push(pending[0]);pending=[];return;}const latest=pending[pending.length-1];const points=pending.reduce((sum,item)=>sum+item.pointsDelta,0);result.push({...latest,id:`aggregate:${pending.map(item=>item.id).join(':')}`,title:`${pending.length} ${t('updates')} · ${latest.title}`,message:`${pending.length} ${t('related gamification updates were grouped to avoid notification spam.')}`,pointsDelta:points});pending=[];};
        for(const item of group){if(pending.length===0){pending=[item];continue;}const previous=pending[pending.length-1];const windowMs=Math.max(0,item.presentation.aggregateWindowMillis??0);if(windowMs>0&&new Date(item.occurredAt).getTime()-new Date(previous.occurredAt).getTime()<=windowMs)pending.push(item);else{flush();pending=[item];}}flush();
    }
    return result.sort((a,b)=>new Date(b.occurredAt).getTime()-new Date(a.occurredAt).getTime());
}
