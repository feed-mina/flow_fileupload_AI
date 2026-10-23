package dev.mina.flow.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
class DatabaseOperationsTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17.6-alpine").withInitScript("least-privilege-test.sql");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",()->"flow_app");p.add("spring.datasource.password",()->"disposable-app-only");
        p.add("spring.flyway.url",postgres::getJdbcUrl);p.add("spring.flyway.user",postgres::getUsername);p.add("spring.flyway.password",postgres::getPassword);
        p.add("app.upload-root",()->System.getProperty("java.io.tmpdir")+"/flow-dbops-"+postgres.getDatabaseName()+"-"+postgres.getMappedPort(5432));
    }
    @Autowired JdbcTemplate app;
    @Autowired PolicyService service;
    @Autowired PolicyRepository repository;
    @Autowired PlatformTransactionManager manager;
    private JdbcTemplate admin(){return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));}
    @BeforeEach void reset(){var a=admin();a.update("DELETE FROM policy_change");a.update("DELETE FROM extension_policy WHERE kind='CUSTOM'");a.update("UPDATE policy_scope SET version=0");}
    @Test void appRoleCanWorkButCannotAdministerOrRewriteAudit(){
        assertThat(app.queryForObject("SELECT current_user",String.class)).isEqualTo("flow_app");
        var policy=service.addCustom("sh","\"0\"");assertThat(policy.customCount()).isEqualTo(1);
        service.setFixed("exe",true,"\"1\"");service.deleteCustom(policy.custom().get(0).id(),"\"2\"");
        for(String denied:List.of("CREATE TABLE should_fail(id int)","CREATE ROLE should_fail", "DROP TABLE extension_policy", "DELETE FROM policy_change", "UPDATE policy_change SET enabled=false", "UPDATE extension_policy SET extension='bad' WHERE extension='exe'"))
            assertThatThrownBy(()->app.execute(denied)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(admin().queryForObject("SELECT COUNT(*) FROM policy_change",Integer.class)).isEqualTo(3);
    }
    @Test void measureRealDatabaseReadWriteLatencyAndQueryPlans()throws Exception {
        var rows=new ArrayList<Map<String,Object>>();
        for(int count:new int[]{0,50,200}){
            reset();for(int i=0;i<count;i++)admin().update("INSERT INTO extension_policy(policy_id,extension,kind,enabled) VALUES(1,?,'CUSTOM',true)","x"+i);
            for(int i=0;i<20;i++)service.get();
            var read=new ArrayList<Double>();long start=System.nanoTime();
            for(int i=0;i<200;i++){long t=System.nanoTime();assertThat(service.get().customCount()).isEqualTo(count);read.add((System.nanoTime()-t)/1_000_000.0);}
            double readSeconds=(System.nanoTime()-start)/1_000_000_000.0;
            var write=Collections.synchronizedList(new ArrayList<Double>());var pool=Executors.newFixedThreadPool(4);start=System.nanoTime();
            try{
                var tasks=new ArrayList<Callable<Void>>();for(int i=0;i<40;i++)tasks.add(()->{long t=System.nanoTime();new TransactionTemplate(manager).execute(status->{repository.lockVersion();repository.setFixed("js",true);repository.advanceVersion();repository.audit("FIXED_SET","js",true);return null;});write.add((System.nanoTime()-t)/1_000_000.0);return null;});
                for(var f:pool.invokeAll(tasks))f.get(20,TimeUnit.SECONDS);
            }finally{pool.shutdownNow();}
            double writeSeconds=(System.nanoTime()-start)/1_000_000_000.0;
            String plan=String.join("\n",app.queryForList("EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) SELECT id,extension,enabled FROM extension_policy WHERE policy_id=1 AND kind='CUSTOM' ORDER BY sort_order,id",String.class));
            var row=new LinkedHashMap<String,Object>();row.put("customCount",count);row.put("readSamples",200);row.put("writeSamples",40);row.put("writeConcurrency",4);
            row.put("read",stats(read));row.put("write",stats(write));row.put("readOpsPerSecond",200/readSeconds);row.put("writeOpsPerSecond",40/writeSeconds);row.put("explainAnalyze",plan);rows.add(row);
            assertThat(service.get().customCount()).isEqualTo(count);assertThat(service.get().version()).isEqualTo(40);
        }
        var report=Map.of("at",java.time.Instant.now().toString(),"database",postgres.getDockerImageName(),"scope","isolated DB/service; not HTTP or Hostinger production", "rows",rows);
        String json=new ObjectMapper().writeValueAsString(report);Files.createDirectories(Path.of("test-results"));Files.writeString(Path.of("test-results/db-performance.json"),json);System.out.println("DB_PERFORMANCE "+json);
    }
    private Map<String,Double> stats(List<Double> values){var sorted=new ArrayList<>(values);Collections.sort(sorted);return Map.of("p50Ms",sorted.get((int)Math.ceil(sorted.size()*.50)-1),"p95Ms",sorted.get((int)Math.ceil(sorted.size()*.95)-1),"p99Ms",sorted.get((int)Math.ceil(sorted.size()*.99)-1));}
}
