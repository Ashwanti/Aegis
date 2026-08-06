package com.aegisteam.aegis.api.ingestion;

import com.aegisteam.aegis.api.ingestion.dto.IngestionResponse;
import com.aegisteam.aegis.core.user.UserPrincipal;
import com.aegisteam.aegis.ingestion.IngestionResult;
import com.aegisteam.aegis.ingestion.StatementIngestionService;
import com.aegisteam.aegis.ingestion.StatementParseException;
import com.aegisteam.aegis.security.CurrentUser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Document intake. The format is not a request parameter: it comes from
 * {@code sources.parser_bean}, so a source cannot be tricked into having its
 * documents read as something they are not.
 *
 * <p>Authentication is enforced by {@code SecurityConfig}'s
 * {@code anyRequest().authenticated()} — this path is outside
 * {@code /api/v1/auth/**}, so it needs a Bearer token.
 */
@RestController
@RequestMapping("/api/v1/sources/{sourceId}/statements")
public class StatementIngestionController {

    private final StatementIngestionService ingestionService;

    public StatementIngestionController(StatementIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestionResponse> upload(
            @PathVariable UUID sourceId,
            @RequestParam("file") MultipartFile file,
            @CurrentUser UserPrincipal principal) {

        if (file == null || file.isEmpty()) {
            throw new StatementParseException("Uploaded file is empty");
        }

        String content = read(file);
        IngestionResult result = ingestionService.ingest(sourceId, principal.getOrgId(), content);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(IngestionResponse.of(sourceId.toString(), "MT940", result));
    }

    /**
     * SWIFT messages are restricted to an ASCII subset, so UTF-8 decoding is
     * lossless for well-formed MT940 and still tolerates UTF-8 documents that
     * picked up accented characters in a :86: narrative.
     */
    private static String read(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StatementParseException("Could not read the uploaded file", e);
        }
    }
}
