package com.example.editor.project.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.example.editor.project.LocalProjectProperties;
import com.example.editor.project.ProjectPathPicker;

class ProjectPathPickerConditionalBeanTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(LocalProjectProperties.class)
            .withUserConfiguration(ProjectPathPickerConfiguration.class);

    @Test void registersHeadlessFallbackWhenNativePickerIsDisabled() {
        contextRunner.withPropertyValues("dsl.projects.local.native-path-picker-enabled=false").run(context -> {
            assertThat(context.getBeansOfType(ProjectPathPicker.class)).hasSize(1);
            assertThat(context.getBean(ProjectPathPicker.class)).isInstanceOf(HeadlessProjectPathPicker.class);
        });
    }

    @Test void registersSwingPickerWhenNativePickerIsEnabled() {
        contextRunner.withPropertyValues("dsl.projects.local.native-path-picker-enabled=true").run(context -> {
            assertThat(context.getBeansOfType(ProjectPathPicker.class)).hasSize(1);
            assertThat(context.getBean(ProjectPathPicker.class)).isInstanceOf(SwingProjectPathPicker.class);
        });
    }

    @Test void preservesDesktopDefaultWhenPropertyIsOmitted() {
        contextRunner.run(context -> {
            assertThat(context.getBeansOfType(ProjectPathPicker.class)).hasSize(1);
            assertThat(context.getBean(ProjectPathPicker.class)).isInstanceOf(SwingProjectPathPicker.class);
        });
    }
}
