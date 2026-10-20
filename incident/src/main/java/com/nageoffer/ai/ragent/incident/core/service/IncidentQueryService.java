package com.nageoffer.ai.ragent.incident.core.service;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.diagnosis.service.IncidentSnapshotService;
import java.time.OffsetDateTime;
import java.text.Normalizer;
import java.util.*;
import org.springframework.stereotype.Service;

/** Bounded personnel projections; never reads raw Alert payloads. */
@Service
public final class IncidentQueryService {
    private final IncidentMapper incidents;
    private final AlertMapper alerts;
    public IncidentQueryService(IncidentMapper incidents, AlertMapper alerts) {
        this.incidents=incidents; this.alerts=alerts;
    }
    public Map<String,Object> list(int limit,String state,String afterTime,String afterId) {
        return list(limit,state,afterTime,afterId,null,null,null,null);
    }
    public Map<String,Object> list(int limit,String state,String afterTime,String afterId,
            String q,String service,String environment,String severity) {
        staff(); limit(limit); id(afterId);
        q=filter(q); service=filter(service); environment=filter(environment); severity=filter(severity);
        if(severity!=null) {
            severity=severity.toUpperCase(Locale.ROOT);
            if(!Set.of("CRITICAL","HIGH","MEDIUM","LOW").contains(severity))throw new IllegalArgumentException();
        }
        if(state!=null && !Set.of("OPEN","ACKNOWLEDGED","MITIGATING","RESOLVED","CLOSED").contains(state)) throw new IllegalArgumentException();
        if((afterTime==null)!=(afterId==null)) throw new IllegalArgumentException();
        OffsetDateTime time=null;
        if(afterTime!=null) try {time=OffsetDateTime.parse(afterTime);} catch(Exception e){throw new IllegalArgumentException();}
        var rows=incidents.findOperationsPage(state,time,afterId,limit+1,q,service,environment,severity);
        boolean more=rows.size()>limit;
        var page=rows.subList(0,Math.min(limit,rows.size()));
        var out=new LinkedHashMap<String,Object>();
        out.put("items",page.stream().map(IncidentQueryService::view).toList());out.put("hasMore",more);
        out.put("nextCursor",more ? Map.of("afterEpisodeStartAt",page.get(page.size()-1).getEpisodeStartAt().toString(),"afterId",page.get(page.size()-1).getId()) : null);
        return out;
    }
    public Map<String,Object> alerts(String incidentId,int limit,String afterId) {
        staff();id(incidentId);id(afterId);limit(limit);
        if(incidents.findById(incidentId)==null) throw new IncidentTransitionService.NotFoundException();
        var rows=alerts.findOperationsPage(incidentId,afterId,limit+1);
        boolean more=rows.size()>limit;
        var page=rows.subList(0,Math.min(limit,rows.size()));
        var out=new LinkedHashMap<String,Object>();out.put("incidentId",incidentId);
        out.put("items",page.stream().map(a->{
            var v=new LinkedHashMap<String,Object>();v.put("alertId",a.getId());
            v.put("sourceId",safe(a.getSourceId()));v.put("ruleId",safe(a.getRuleId()));v.put("title",safe(a.getTitle()));
            v.put("severity",safe(a.getSeverity()));v.put("service",safe(a.getService()));v.put("environment",safe(a.getEnvironment()));
            v.put("resourceType",safe(a.getResourceType()));v.put("resourceId",safe(a.getResourceId()));
            v.put("occurredAt",a.getOccurredAt().toString());v.put("receivedAt",a.getReceivedAt().toString());return v;
        }).toList());out.put("hasMore",more);out.put("nextAfterId",more?page.get(page.size()-1).getId():null);return out;
    }
    private static Map<String,Object> view(IncidentMapper.IncidentRow r) {
        var view=new LinkedHashMap<String,Object>();
        view.put("incidentId",r.getId());view.put("lifecycleState",r.getLifecycleState());view.put("version",r.getVersion());
        view.put("alertCount",r.getAlertCount());view.put("episodeStartAt",r.getEpisodeStartAt().toString());
        view.put("firstAlertReceivedAt",r.getFirstAlertReceivedAt().toString());view.put("lastAlertReceivedAt",r.getLastAlertReceivedAt().toString());
        view.put("summary",r.getSummaryTitle()==null?null:Map.of("title",safe(r.getSummaryTitle()),
            "service",safe(r.getSummaryService()),"environment",safe(r.getSummaryEnvironment()),"severity",safe(r.getSummarySeverity())));
        return view;
    }
    private static String filter(String text) {
        if(text==null)return null;
        if(text.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException();
        var normalized=Normalizer.normalize(text.strip(),Normalizer.Form.NFC);
        if(normalized.codePointCount(0,normalized.length())>128)throw new IllegalArgumentException();
        return normalized.isEmpty()?null:normalized;
    }
    private static String safe(String text){return IncidentSnapshotService.safe(text);}
    private static void staff(){StpUtil.checkLogin();StpUtil.checkRole("admin");}
    private static void limit(int n){if(n<1 || n>100)throw new IllegalArgumentException();}
    private static void id(String id){if(id!=null && !id.matches("[0-9]{1,20}"))throw new IllegalArgumentException();}
}
