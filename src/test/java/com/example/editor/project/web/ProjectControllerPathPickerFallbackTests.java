package com.example.editor.project.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.ResolvableType;
import org.springframework.beans.factory.ObjectProvider;

import com.example.editor.project.ProjectPathPicker;
import com.example.editor.project.storage.HeadlessProjectPathPicker;

class ProjectControllerPathPickerFallbackTests {
    @Test
    void emptyProviderCanResolveSafeHeadlessFallback() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        ObjectProvider<ProjectPathPicker> provider = factory.getBeanProvider(ResolvableType.forClass(ProjectPathPicker.class));
        ProjectPathPicker picker = provider.getIfAvailable(HeadlessProjectPathPicker::new);
        assertThat(picker).isInstanceOf(HeadlessProjectPathPicker.class);
    }
}
