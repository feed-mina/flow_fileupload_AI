package dev.mina.flow.upload;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.apache.tika.Tika;
import org.apache.tika.mime.MimeTypes;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Diagnostic matrix only. No content gate is silently added to the blacklist upload contract. */
class ContentExtensionMatrixTest {
    private final Tika tika=new Tika();
    private String compare(String extension,byte[] bytes)throws Exception {
        String expected=MimeTypes.getDefaultMimeTypes().getMimeType("fixture."+extension).getName();
        String actual=tika.detect(new ByteArrayInputStream(bytes)); // Never provide filename/MIME to actual detection.
        if(expected.equals("application/octet-stream")||actual.equals("application/octet-stream")||expected.equals("text/plain"))return "UNVERIFIABLE";
        return expected.equals(actual)?"MATCH":"MISMATCH";
    }
    @Test void recognizableFormatsHaveMatchAndRenamedMismatchCases()throws Exception {
        var fixtures=new LinkedHashMap<String,byte[]>();
        fixtures.put("pdf","%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        fixtures.put("png",new byte[]{(byte)137,80,78,71,13,10,26,10,0,0,0,13,73,72,68,82});
        fixtures.put("gif","GIF89a\u0001\u0000\u0001\u0000".getBytes(StandardCharsets.ISO_8859_1));
        var evidence=new ArrayList<Map<String,String>>();
        for(var item:fixtures.entrySet()){
            String match=compare(item.getKey(),item.getValue());assertThat(match).as(item.getKey()).isEqualTo("MATCH");
            String other=item.getKey().equals("pdf")?"png":"pdf";
            String mismatch=compare(other,item.getValue());assertThat(mismatch).isEqualTo("MISMATCH");
            evidence.add(Map.of("extension",item.getKey(),"actualMime",tika.detect(item.getValue()),"sameName",match,"renamedTo",other,"renamedResult",mismatch));
        }
        assertThat(compare("notregistered",fixtures.get("pdf"))).isEqualTo("UNVERIFIABLE");
        assertThat(compare("txt","arbitrary text".getBytes(StandardCharsets.UTF_8))).isEqualTo("UNVERIFIABLE");
        // Cover the maximum policy cardinality: fixed seven + two hundred custom names.
        var policyExtensions=new ArrayList<>(List.of("bat","cmd","com","cpl","exe","scr","js"));
        for(int i=0;i<200;i++)policyExtensions.add("custom"+i);
        for(String extension:policyExtensions){String result=compare(extension,fixtures.get("png"));assertThat(result).isIn("MISMATCH","UNVERIFIABLE");}
        var report=Map.of("engine","Apache Tika core 3.2.3 (test dependency)","matchedAndMismatched",evidence,"policyNamesChecked",policyExtensions.size(),"unknownResult","UNVERIFIABLE","scope","diagnostic only; arbitrary extension/contents cannot be proven; not antivirus; upload gate unchanged");
        Files.createDirectories(Path.of("test-results"));String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(report);Files.writeString(Path.of("test-results/content-extension-matrix.json"),json);System.out.println("CONTENT_MATRIX "+json);
    }
}
