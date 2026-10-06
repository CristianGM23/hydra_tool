package com.example.editor.config.governance;

import java.util.List;

public record HydraTechnicalSettingsSnapshot(List<String> activeProfiles, List<HydraTechnicalSetting> settings) { }
