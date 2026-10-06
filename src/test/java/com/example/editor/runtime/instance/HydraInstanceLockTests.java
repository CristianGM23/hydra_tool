package com.example.editor.runtime.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.example.editor.config.StorageProperties;

class HydraInstanceLockTests {
    @TempDir Path temporary;

    @Test void rejectsASecondProcessLockAndAllowsItAfterRelease() {
        HydraInstanceLockProperties firstProperties = properties();
        HydraInstanceLockProperties secondProperties = properties();
        HydraInstanceLock first = new HydraInstanceLock(firstProperties);
        HydraInstanceLock second = new HydraInstanceLock(secondProperties);
        try {
            first.acquire();
            assertThatThrownBy(second::acquire).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Another HYDRA process");
            first.release();
            assertThatCode(second::acquire).doesNotThrowAnyException();
        } finally {
            first.release();
            second.release();
        }
    }

    @Test void derivesTheLockFromConfiguredStorageWhenNoOverrideExists() {
        Path data = temporary.resolve("custom-data");
        StorageProperties storage = new StorageProperties(data.resolve("metamodels"), data.resolve("models"),
                data.resolve("notations"), data.resolve("validations"), data.resolve("quick-fixes"),
                data.resolve("generators"), data.resolve("assets"), data.resolve("exports"), true);
        HydraInstanceLockProperties properties = new HydraInstanceLockProperties();
        properties.setEnabled(true);
        HydraInstanceLock instanceLock = new HydraInstanceLock(properties, storage);
        try {
            instanceLock.acquire();
            assertThatCode(() -> java.nio.file.Files.readString(data.resolve(".hydra.lock"))).doesNotThrowAnyException();
        } finally {
            instanceLock.release();
        }
    }

    @Test void springBootstrapUsesConditionalBeanAndExplicitInjectionConstructor() throws Exception {
        ConditionalOnProperty condition = HydraInstanceLock.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("hydra.instance-lock");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isTrue();

        var constructor = HydraInstanceLock.class.getDeclaredConstructor(
                HydraInstanceLockProperties.class, StorageProperties.class);
        assertThat(constructor.isAnnotationPresent(Autowired.class)).isTrue();
    }

    private HydraInstanceLockProperties properties() {
        HydraInstanceLockProperties properties = new HydraInstanceLockProperties();
        properties.setPath(temporary.resolve("data/.hydra.lock"));
        properties.setEnabled(true);
        return properties;
    }
}
