package com.example.editor.standalone.publication;

/** Update query issued by a standalone distribution. */
public record UpdateCheckRequest(String productId, String currentVersion,
        PublicationChannel channel, String platform) {
}
