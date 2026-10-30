package com.example.editor.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException.Reason;

/**
 * Concurrent writes to a document of the shared JSON store (textual sessions persist through it directly) lose the optimistic check:
 * the losers must receive 412/409 with the store's reason, never a generic 500 (found by the battery scenario CONC, part C).
 */
class DocumentStoreConflictMappingTests {
    @RestController
    static class Throwing {
        @PostMapping("/store/{reason}")
        void fail(@PathVariable("reason") String reason) {
            throw new CanonicalDocumentStoreException(Reason.valueOf(reason), "Document has changed since it was loaded");
        }

        @PostMapping("/store-io")
        void failWritingTheFile() {
            throw new CanonicalDocumentStoreException(Reason.STORAGE_ERROR, "Could not store document: C:\\srv\\hydra\\data\\secret-project\\doc.json",
                    new java.io.IOException("disk full"));
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Throwing()).setControllerAdvice(new ApiExceptionHandler()).build();

    @Test
    void anOptimisticConflictOfTheDocumentStoreIsAPreconditionFailureNotAServerError() throws Exception {
        mvc.perform(post("/store/PRECONDITION_FAILED")).andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.message").value("Document has changed since it was loaded"));
    }

    @Test
    void everyStoreReasonKeepsItsHttpMeaning() throws Exception {
        mvc.perform(post("/store/CONFLICT")).andExpect(status().isConflict());
        mvc.perform(post("/store/NOT_FOUND")).andExpect(status().isNotFound());
        mvc.perform(post("/store/INVALID_REQUEST")).andExpect(status().isBadRequest());
        mvc.perform(post("/store/STORAGE_ERROR")).andExpect(status().isInternalServerError());
    }

    /**
     * A storage failure is a server error: it must not hand the server's paths or cause to the client. Since T-SEC every subsystem answers a
     * storage failure with its distinguishable STORAGE_ERROR code and the public storage message (jar 13 answered INTERNAL_ERROR here only).
     */
    @Test
    void aStorageFailureDoesNotDiscloseServerPathsToTheClient() throws Exception {
        mvc.perform(post("/store-io")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STORAGE_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-project"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("C:\\srv"))));
    }
}
