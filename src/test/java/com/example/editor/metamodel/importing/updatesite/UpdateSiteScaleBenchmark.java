package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit, opt-in scale benchmark. Run with -Dtest=UpdateSiteScaleBenchmark test. */
class UpdateSiteScaleBenchmark {
    @TempDir Path temporary;

    @Test
    void benchmarksOneTenAndFiftyThousandEntries() {
        for(int count:new int[]{1_000,10_000,50_000}) benchmark(count);
    }

    private void benchmark(int count) {
        UpdateSiteImportProperties properties=new UpdateSiteImportProperties();
        properties.setStorageRoot(temporary.resolve("run-"+count).toString());
        properties.setMaxEntries(60_000);
        properties.setMaxUploadBytes(256L*1024L*1024L);
        properties.setMaxUncompressedBytes(512L*1024L*1024L);
        properties.setMaxArchiveReadMillis(3_600_000L);
        properties.setMaxArchiveCpuMillis(3_600_000L);
        UpdateSiteArchiveReader reader=new UpdateSiteArchiveReader(properties);
        byte[] content="benchmark-entry".getBytes(StandardCharsets.UTF_8);
        List<UpdateSiteInputFile> inputs=new ArrayList<>(count);
        for(int index=0;index<count;index++) inputs.add(new UpdateSiteInputFile("files/entry-"+index+".txt",content));
        long started=System.nanoTime();
        UpdateSiteRepositoryContent repository=reader.read(inputs);
        long elapsed=(System.nanoTime()-started)/1_000_000L;
        System.out.printf("HYDRA_UPDATE_SITE_BENCHMARK entries=%d elapsedMillis=%d bytes=%d%n",count,elapsed,repository.totalBytes());
        assertThat(repository.files()).hasSize(count);
    }
}
