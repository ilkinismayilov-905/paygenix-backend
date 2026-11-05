package com.example.paygenix.service;

import com.example.paygenix.dto.LegalDocumentRef;
import com.example.paygenix.entity.LegalDocument;
import com.example.paygenix.exceptions.ApiException;
import com.example.paygenix.repository.LegalDocumentRepository;
import com.example.paygenix.repository.UserLegalAcceptanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** T-121: which Terms/Privacy versions a user accepted; money moves need the current ones. */
@Service
@RequiredArgsConstructor
public class LegalAcceptanceService {

    private final LegalDocumentRepository documents;
    private final UserLegalAcceptanceRepository acceptances;

    @Transactional(readOnly = true)
    public List<LegalDocumentRef> pending(Long userId) {
        return documents.findPendingFor(userId).stream()
                .map(d -> new LegalDocumentRef(d.getType(), d.getVersion()))
                .toList();
    }

    /**
     * Records the versions the user was shown. Each must be the active one, and together with earlier
     * acceptances they must cover every active document; otherwise 409 and nothing is recorded.
     */
    @Transactional
    public void accept(Long userId, List<LegalDocumentRef> shown) {
        for (LegalDocumentRef ref : shown) {
            LegalDocument doc = documents.findByTypeAndIsActiveTrue(ref.type())
                    .filter(d -> d.getVersion().equals(ref.version()))
                    .orElseThrow(LegalAcceptanceService::outdated);
            acceptances.acceptOnce(userId, doc.getId());
        }
        if (!documents.findPendingFor(userId).isEmpty()) {
            throw outdated();
        }
    }

    @Transactional(readOnly = true)
    public void requireAccepted(Long userId) {
        if (!documents.findPendingFor(userId).isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LEGAL_ACCEPTANCE_REQUIRED",
                    "Accept the current Terms and Privacy Policy first");
        }
    }

    private static ApiException outdated() {
        return new ApiException(HttpStatus.CONFLICT, "LEGAL_VERSION_OUTDATED",
                "These are not the current documents; load them again");
    }
}
