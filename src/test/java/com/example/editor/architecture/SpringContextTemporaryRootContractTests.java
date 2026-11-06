package com.example.editor.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertySource;

/**
 * INC-TEST-TEMPDIR-CLEANUP: a full Spring context whose directories are registered under a static @TempDir must be closed before JUnit
 * deletes that directory. Left in the Spring test context cache, its asynchronous audit writer may recreate audit-log while JUnit deletes
 * the root, and the class fails on close with "Failed to delete temp directory" although its tests pass.
 *
 * Checked on the compiled classes, not on the source text: comments and strings do not count, inherited and meta-annotations do, and only
 * a static @TempDir that the context can reach (the class also declares a @DynamicPropertySource) requires the context to be closed.
 */
class SpringContextTemporaryRootContractTests {
    private static final Set<DirtiesContext.ClassMode> CLOSES_BEFORE_THE_ROOT_IS_DELETED =
            Set.of(DirtiesContext.ClassMode.AFTER_CLASS, DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD);

    @Test
    void everySpringBootTestWithAStaticTemporaryRootClosesItsContextAfterTheClass() throws Exception {
        // only the compiled test classes are read: no production class declares @SpringBootTest
        String testClasses = getClass().getProtectionDomain().getCodeSource().getLocation().toString();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        CachingMetadataReaderFactory readers = new CachingMetadataReaderFactory(resolver);
        AnnotationTypeFilter springBootTests = new AnnotationTypeFilter(SpringBootTest.class);
        List<String> wired = new ArrayList<>();
        List<String> unprotected = new ArrayList<>();
        for(Resource compiled : resolver.getResources(testClasses + "com/example/editor/**/*.class")) {
            MetadataReader reader = readers.getMetadataReader(compiled);
            if(!springBootTests.match(reader, readers)) continue;
            Class<?> type = Class.forName(reader.getClassMetadata().getClassName(), false, getClass().getClassLoader());
            if(!hasStaticTemporaryRoot(type) || !registersDynamicProperties(type)) continue;
            wired.add(type.getName());
            DirtiesContext dirties = AnnotatedElementUtils.findMergedAnnotation(type, DirtiesContext.class);
            if(dirties == null || !CLOSES_BEFORE_THE_ROOT_IS_DELETED.contains(dirties.classMode())) unprotected.add(type.getName());
        }
        assertThat(wired).as("@SpringBootTest classes whose context uses a static @TempDir").isNotEmpty();
        assertThat(unprotected).as("classes that leave their context alive while JUnit deletes the static @TempDir").isEmpty();
    }

    private static boolean hasStaticTemporaryRoot(Class<?> type) {
        for(Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for(Field field : current.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers()) && field.isAnnotationPresent(TempDir.class)) return true;
            }
        }
        return false;
    }

    private static boolean registersDynamicProperties(Class<?> type) {
        for(Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for(Method method : current.getDeclaredMethods()) {
                if(Modifier.isStatic(method.getModifiers()) && method.isAnnotationPresent(DynamicPropertySource.class)) return true;
            }
        }
        return false;
    }
}
