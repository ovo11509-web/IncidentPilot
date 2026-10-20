package com.nageoffer.ai.ragent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Instant;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Explicit test-scope launcher, never a default Surefire test. Stop by creating STOP. */
public final class IncidentOperationsUiFixture {
    private static final ObjectMapper JSON=new ObjectMapper();
    public static void main(String[] args) throws Exception {
        System.setProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        BootstrapIsolatedStartupTest.runIsolatedApplications(true,args.length>0 && "no-mapping".equals(args[0])?"diagnosis-ui-no-mapping":"diagnosis-ui");
    }
    static void serve(int port,PostgreSQLContainer mainDb,PostgreSQLContainer bitDb,String token,String password,List<Path> logs) throws Exception {
        try(var connection=java.sql.DriverManager.getConnection(mainDb.getJdbcUrl(),mainDb.getUsername(),mainDb.getPassword());
            var user=connection.prepareStatement("INSERT INTO t_user(id,username,password,role) VALUES ('43','isolated-reader',?,'user')")) {
            user.setString(1,password);user.executeUpdate();
        }
        String runId="spec-i-"+Instant.now().toString().replaceAll("[:.]","-");
        Path proof=Path.of("..","incident","target","ui-evaluation",runId).toAbsolutePath();Files.createDirectories(proof);
        Path credentials=Path.of("target","ui-fixture-credentials.json").toAbsolutePath();
        String a=BootstrapIsolatedStartupTest.submitDiagnosisAlert(port,"ui-a1","健康检查失败 探针 password=secret-value",password);
        String b=BootstrapIsolatedStartupTest.submitDiagnosisAlert(port,"ui-a2","健康检查失败 探针",password);
        for(int i=0;i<21;i++)BootstrapIsolatedStartupTest.seedIncident(mainDb,String.valueOf(1000+i),String.valueOf(2000+i),"ui-list-"+i,"alert-incident-v1:ui-list-"+i,String.valueOf(3000+i));
        String incidentId="";
        for(int i=0;i<150;i++) {
            incidentId=BootstrapIsolatedStartupTest.value(mainDb,"SELECT coalesce(incident_id,'') FROM t_incident_alert WHERE id='"+a+"'");
            if(incidentId!=null && !incidentId.isBlank() && incidentId.equals(BootstrapIsolatedStartupTest.value(mainDb,"SELECT coalesce(incident_id,'') FROM t_incident_alert WHERE id='"+b+"'")))break;
            Thread.sleep(200);
        }
        if(incidentId==null || incidentId.isBlank())throw new IllegalStateException("alerts not aggregated");
        String unknown=BootstrapIsolatedStartupTest.propose(port,"ui-unknown-display",token);
        String pending=BootstrapIsolatedStartupTest.propose(port,"ui-workbench-pending",token);
        BootstrapIsolatedStartupTest.execute(mainDb,"UPDATE t_incident_action SET state='UNKNOWN',authorization_due_at=clock_timestamp()+interval '5 minutes',version=version+1 WHERE id='"+unknown+"'");
        // Display-only adversarial input; isolated owner SQL is not evidence of an actual execution.
        BootstrapIsolatedStartupTest.execute(mainDb,"UPDATE t_incident_alert SET title='<img src=https://invalid.example/ui-injection onerror=alert(1)> token=secret-value' WHERE id='31'");
        int vitePort;try(var socket=new ServerSocket(0,0,InetAddress.getByName("127.0.0.1"))){vitePort=socket.getLocalPort();}
        Process vite=new ProcessBuilder("node","tests/incident-browser-fixture.mjs",String.valueOf(port),String.valueOf(vitePort),proof.toString())
            .directory(Path.of("..","frontend").toFile()).redirectErrorStream(true).redirectOutput(proof.resolve("vite.log").toFile()).start();
        try {
        var metadata=JSON.createObjectNode().put("runId",runId).put("appPort",port).put("browserUrl","http://127.0.0.1:"+vitePort+"/admin/workbench")
            .put("incidentId",incidentId).put("pendingActionId",pending).put("unknownActionId",unknown).put("unknownFixtureIncidentId","100").put("injectionFixtureIncidentId","300")
            .put("isolation","temporary main PostgreSQL, simulation PostgreSQL, Redis, storage, Bootstrap and MCP JVMs")
            .put("unknownScope","display fixture only, not runtime UNKNOWN proof");
        Files.writeString(proof.resolve("environment.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(metadata));
        var login=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/ragent/auth/login"))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("username","isolated-reader","password",password)))).build(),HttpResponse.BodyHandlers.ofString());
        String readerToken=JSON.readTree(login.body()).path("data").path("token").asText();
        if(readerToken.length()<16)throw new IllegalStateException("reader login failed");
        var security=JSON.createArrayNode();
        for(var auth:List.of("",readerToken))for(var path:List.of("/workbench","/incidents","/incidents/"+incidentId,"/incidents/"+incidentId+"/alerts","/incidents/"+incidentId+"/timeline","/incidents/"+incidentId+"/diagnoses","/incidents/"+incidentId+"/actions")) {
            var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/ragent/incident/v1"+path));if(!auth.isEmpty())builder.header("Authorization",auth);
            var response=HttpClient.newHttpClient().send(builder.GET().build(),HttpResponse.BodyHandlers.ofString());
            int expected=auth.isEmpty()?401:403;if(response.statusCode()!=expected)throw new IllegalStateException("personnel gate failed "+path+" "+response.statusCode());
            security.addObject().put("path",path).put("role",auth.isEmpty()?"anonymous":"user").put("httpStatus",response.statusCode()).put("code",JSON.readTree(response.body()).path("code").asText());
        }
        Files.writeString(proof.resolve("security-gates.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(security));
        Files.writeString(credentials,JSON.writeValueAsString(Map.of("username","isolated-admin","password",password)));
        Files.writeString(Path.of("target","ui-fixture-ready.json"),JSON.writeValueAsString(Map.of("proof",proof.toString(),"credentials",credentials.toString())));
        System.out.println("UI fixture ready: "+proof);
            long deadline=System.nanoTime()+java.time.Duration.ofHours(2).toNanos();
            int snapshot=0;
            while(!Files.exists(proof.resolve("STOP")) && System.nanoTime()<deadline) {
                if(Files.exists(proof.resolve("STALE"))) {
                    BootstrapIsolatedStartupTest.submitDiagnosisAlert(port,"ui-a3","健康检查失败 探针",password);
                    Files.delete(proof.resolve("STALE"));
                }
                var out=JSON.createObjectNode().put("capturedAt",Instant.now().toString())
                    .put("simulationApplications",BootstrapIsolatedStartupTest.count(bitDb,"simulation_application"))
                    .put("simulationState",BootstrapIsolatedStartupTest.value(bitDb,"SELECT state FROM simulation_target WHERE target_id='demo'"));
                for(var path:List.of("/workbench","/incidents/"+incidentId,"/incidents/"+incidentId+"/alerts","/incidents/"+incidentId+"/timeline","/incidents/"+incidentId+"/diagnoses","/incidents/"+incidentId+"/actions")) {
                    var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/ragent/incident/v1"+path)).header("Authorization",token).GET().build(),HttpResponse.BodyHandlers.ofString());
                    if(response.statusCode()==200)out.set(path,JSON.readTree(response.body()));
                }
                out.set("databaseCounts",JSON.readTree(BootstrapIsolatedStartupTest.value(mainDb,"SELECT json_build_object('activeIncidents',(SELECT count(*) FROM t_incident WHERE lifecycle_state IN ('OPEN','ACKNOWLEDGED','MITIGATING')),'pendingApprovalActions',(SELECT count(*) FROM t_incident_action WHERE state='PENDING_APPROVAL'),'executingActions',(SELECT count(*) FROM t_incident_action WHERE state='EXECUTING'),'unknownActions',(SELECT count(*) FROM t_incident_action WHERE state='UNKNOWN'))::text")));
                String safe=JSON.writerWithDefaultPrettyPrinter().writeValueAsString(out);
                if(safe.contains(password) || safe.contains(token) || safe.contains("secret-value"))throw new IllegalStateException("secret in evidence");
                Files.writeString(proof.resolve("latest-server-evidence.json"),safe);
                if(Files.exists(proof.resolve("CAPTURE"))){Files.writeString(proof.resolve("server-evidence-"+(++snapshot)+".json"),safe);Files.delete(proof.resolve("CAPTURE"));}
                Thread.sleep(1000);
            }
        } finally {
            vite.destroyForcibly().waitFor();Files.deleteIfExists(credentials);Files.deleteIfExists(Path.of("target","ui-fixture-ready.json"));
            for(Path log:logs) {
                String content=Files.readString(log);
                for(String secret:List.of(password,token,"isolated-bootstrap-outbound-123456789012345","isolated-bootstrap-callback-123456789012345"))
                    if(content.contains(secret))throw new IllegalStateException("secret in process log");
                Files.copy(log,proof.resolve(log.getFileName()),StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
