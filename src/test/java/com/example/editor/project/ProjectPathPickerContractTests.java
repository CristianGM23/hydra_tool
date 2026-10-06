package com.example.editor.project;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import com.example.editor.project.storage.SwingProjectPathPicker;

class ProjectPathPickerContractTests {
    @Test void requestNormalizesMissingValues() {
        ProjectPathPickerRequest request=new ProjectPathPickerRequest(null,null,null);
        assertThat(request.mode()).isEqualTo(ProjectPathPickerMode.DIRECTORY);
        assertThat(request.title()).isEmpty();
        assertThat(request.initialDirectory()).isEmpty();
    }

    @Test void disabledNativePickerReturnsPortableFallback() {
        LocalProjectProperties properties=new LocalProjectProperties();
        properties.setNativePathPickerEnabled(false);
        ProjectPathPickerResult result=new SwingProjectPathPicker(properties).pick(new ProjectPathPickerRequest(ProjectPathPickerMode.PROJECT_DESCRIPTOR,"",""));
        assertThat(result.available()).isFalse();
        assertThat(result.cancelled()).isFalse();
        assertThat(result.mode()).isEqualTo(ProjectPathPickerMode.PROJECT_DESCRIPTOR);
        assertThat(result.message()).contains("disabled");
    }

    @Test void resultNormalizesNullableText() {
        ProjectPathPickerResult result=new ProjectPathPickerResult(true,false,null,null,null,null,null);
        assertThat(result.selectedPath()).isEmpty();
        assertThat(result.selectedDirectory()).isEmpty();
        assertThat(result.fileName()).isEmpty();
        assertThat(result.mode()).isEqualTo(ProjectPathPickerMode.DIRECTORY);
    }

    @Test void localOnlyPickerIsEnabledByDefault() {
        LocalProjectProperties properties=new LocalProjectProperties();
        assertThat(properties.isNativePathPickerEnabled()).isTrue();
        assertThat(properties.isNativePathPickerLocalOnly()).isTrue();
    }
}
