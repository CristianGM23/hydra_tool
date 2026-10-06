package com.example.editor.metamodel.importing.updatesite;

import java.time.Instant;
import java.util.List;

/** Current and retained mirror history for one internal release channel. */
public record UpdateSiteMirrorChannelState(UpdateSiteMirrorChannel channel,
        String currentMirrorId, Instant updatedAt, List<String> history) {
    public UpdateSiteMirrorChannelState {
        channel = channel == null ? UpdateSiteMirrorChannel.INTERNAL : channel;
        currentMirrorId = currentMirrorId == null ? "" : currentMirrorId.trim();
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
        history = history == null ? List.of() : List.copyOf(history);
    }
}
