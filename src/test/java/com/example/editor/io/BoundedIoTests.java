package com.example.editor.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoundedIoTests {
    @TempDir Path temporary;

    @Test void readsAFileInsideItsBudget() throws Exception {
        Path file=temporary.resolve("small.txt"); Files.writeString(file,"hydra");
        assertThat(new String(BoundedIo.read(file,16),StandardCharsets.UTF_8)).isEqualTo("hydra");
    }
    @Test void rejectsAFileBeforeAllocatingWhenKnownSizeExceedsBudget() throws Exception {
        Path file=temporary.resolve("large.bin"); Files.write(file,new byte[17]);
        assertThatThrownBy(()->BoundedIo.read(file,16)).isInstanceOf(java.io.IOException.class).hasMessageContaining("configured limit");
    }
    @Test void rejectsAnUnknownLengthStreamAtTheBoundary() {
        assertThatThrownBy(()->BoundedIo.read(new ByteArrayInputStream(new byte[17]),16)).isInstanceOf(java.io.IOException.class).hasMessageContaining("configured limit");
    }
    @Test void acceptsExactlyTheConfiguredLimit() throws Exception {
        assertThat(BoundedIo.read(new ByteArrayInputStream(new byte[16]),16)).hasSize(16);
    }
    @Test void readsUtf8TextWithoutChangingItsBudgetSemantics() throws Exception {
        Path file=temporary.resolve("utf8.txt"); Files.writeString(file,"HYDRA áéí",StandardCharsets.UTF_8);
        assertThat(BoundedIo.readUtf8(file,64)).isEqualTo("HYDRA áéí");
    }
    @Test void rejectsUtf8TextWhenItsEncodedBytesExceedTheBudget() throws Exception {
        Path file=temporary.resolve("utf8-large.txt"); Files.writeString(file,"ááá",StandardCharsets.UTF_8);
        assertThatThrownBy(()->BoundedIo.readUtf8(file,5)).isInstanceOf(java.io.IOException.class).hasMessageContaining("configured limit");
    }

}
