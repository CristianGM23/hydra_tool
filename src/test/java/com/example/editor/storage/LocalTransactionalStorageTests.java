package com.example.editor.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalTransactionalStorageTests {
    @TempDir Path root;
    @Test void roundTripUsesVersionedAtomicContract() throws Exception {
        var store=new LocalTransactionalStorage(root); var first=store.writeAtomically(Path.of("a/doc.json"),"one".getBytes(StandardCharsets.UTF_8),"");
        assertThat(new String(store.read(Path.of("a/doc.json"),32).orElseThrow().content(),StandardCharsets.UTF_8)).isEqualTo("one");
        var second=store.writeAtomically(Path.of("a/doc.json"),"two".getBytes(StandardCharsets.UTF_8),first.version()); assertThat(second.version()).isNotEqualTo(first.version());
    }
    @Test void staleVersionCannotOverwriteNewerData() throws Exception {
        var store=new LocalTransactionalStorage(root); var first=store.writeAtomically(Path.of("doc"),new byte[]{1},""); store.writeAtomically(Path.of("doc"),new byte[]{2},first.version());
        assertThatThrownBy(()->store.writeAtomically(Path.of("doc"),new byte[]{3},first.version())).isInstanceOf(StorageVersionConflictException.class);
    }
    @Test void traversalIsRejected(){assertThatThrownBy(()->new LocalTransactionalStorage(root).exists(Path.of("../escape"))).isInstanceOf(IllegalArgumentException.class);}
}
