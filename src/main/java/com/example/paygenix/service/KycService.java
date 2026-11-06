package com.example.paygenix.service;

import com.example.paygenix.dto.response.KycReviewItem;
import com.example.paygenix.dto.response.KycReviewPage;
import com.example.paygenix.dto.response.KycStatusResponse;
import com.example.paygenix.entity.EditProfile;
import com.example.paygenix.entity.KycSubmission;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.KycDocumentType;
import com.example.paygenix.enums.KycStatus;
import com.example.paygenix.exceptions.ApiException;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.media.KycDocumentStorage;
import com.example.paygenix.repository.EditProfileRepository;
import com.example.paygenix.repository.KycSubmissionRepository;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Identity verification by manual review (T-070, T-071). A user sends one photo of an identity document; it waits as
 * PENDING until an admin approves it (the user becomes VERIFIED) or rejects it with a reason (the user may send a new
 * one). Documents live in a private bucket and are shown to reviewers through short-lived links only. An admin cannot
 * review their own submission.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KycService {

    static final long MAX_DOCUMENT_BYTES = 5L * 1024 * 1024;
    static final int MAX_SUBMISSIONS_PER_DAY = 5;
    private static final String DAILY_PREFIX = "kyc:daily:";

    private final KycSubmissionRepository submissions;
    private final UserRepository userRepository;
    private final EditProfileRepository profileRepository;
    private final KycDocumentStorage storage;
    private final StringRedisTemplate redis;
    private final EmailService emailService;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public KycStatusResponse status(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        KycSubmission latest = submissions.findFirstByUserIdOrderBySubmittedAtDescIdDesc(userId).orElse(null);
        String status = user.getVerificationStatus() == User.VerificationStatus.VERIFIED ? "APPROVED"
                : latest == null ? "NOT_SUBMITTED"
                : latest.getStatus().name();
        return latest == null
                ? new KycStatusResponse(status, null, null, null, null)
                : new KycStatusResponse(status, latest.getDocumentType(), latest.getSubmittedAt(), latest.getReviewedAt(),
                        latest.getRejectionReason());
    }

    public KycStatusResponse submit(Long userId, KycDocumentType documentType, MultipartFile document) {
        if (documentType == null) {
            throw ApiException.validation("documentType: is required");
        }
        if (document == null || document.isEmpty()) {
            throw ApiException.validation("document: is required");
        }
        if (document.getSize() > MAX_DOCUMENT_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "DOCUMENT_TOO_LARGE", "The document photo must be at most 5 MB");
        }
        // cheap checks first, so a refused submission never stores a document or uses up the daily limit
        storage.check(document);
        rejectIfVerifiedOrPending(userId);
        String dailyKey = DAILY_PREFIX + userId;
        Long today = redis.execute(LoginAttemptService.COUNT_ATTEMPT, List.of(dailyKey),
                String.valueOf(Duration.ofDays(1).toSeconds()));
        if (today == null || today > MAX_SUBMISSIONS_PER_DAY) {
            Long ttl = redis.getExpire(dailyKey);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "KYC_SUBMIT_LIMIT", "Too many documents sent today",
                    ttl != null && ttl > 0 ? Duration.ofSeconds(ttl) : Duration.ofDays(1));
        }
        String key;
        try {
            // stored outside the transaction: no DB connection is held during the upload
            key = storage.upload(document, userId);
        } catch (RuntimeException e) {
            redis.opsForValue().decrement(dailyKey); // a storage outage must not use up the user's tries
            throw e;
        }
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                rejectIfVerifiedOrPending(userId);
                submissions.saveAndFlush(new KycSubmission(userId, documentType, key));
            });
        } catch (RuntimeException e) {
            // nothing references the document: remove it (it is personal data) and give the try back
            storage.deleteQuietly(key);
            redis.opsForValue().decrement(dailyKey);
            // a parallel submit won the one-pending slot (uq_kyc_one_pending)
            throw e instanceof DataIntegrityViolationException ? alreadyPending() : e;
        }
        log.info("KYC document submitted by user {}", userId);
        return status(userId);
    }

    private void rejectIfVerifiedOrPending(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getVerificationStatus() == User.VerificationStatus.VERIFIED) {
            throw new ApiException(HttpStatus.CONFLICT, "KYC_ALREADY_VERIFIED", "This account is already verified");
        }
        if (submissions.existsByUserIdAndStatus(userId, KycStatus.PENDING)) {
            throw alreadyPending();
        }
    }

    private static ApiException alreadyPending() {
        return new ApiException(HttpStatus.CONFLICT, "KYC_ALREADY_PENDING", "A document is already waiting for review");
    }

    @Transactional(readOnly = true)
    public KycReviewPage list(KycStatus status, int page, int size) {
        Sort order = status == KycStatus.PENDING
                ? Sort.by("submittedAt").ascending().and(Sort.by("id").ascending())
                : Sort.by("submittedAt").descending().and(Sort.by("id").descending());
        Slice<KycSubmission> slice = submissions.findByStatus(status, PageRequest.of(page, size, order));
        Set<Long> ids = slice.map(KycSubmission::getUserId).toSet();
        Map<Long, User> users = userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, String> names = profileRepository.findByUserIdIn(ids).stream()
                .filter(p -> p.getFullName() != null && !p.getFullName().isBlank())
                .collect(Collectors.toMap(p -> p.getUser().getId(), EditProfile::getFullName, (a, b) -> a));
        return new KycReviewPage(slice.map(k -> item(k, users.get(k.getUserId()), names.get(k.getUserId()), null)).toList(),
                page, size, slice.hasNext());
    }

    // Not @Transactional: presigning may call the storage, and no DB connection should wait on it.
    public KycReviewItem get(Long id) {
        KycSubmission k = submissions.findById(id).orElseThrow(() -> new NotFoundException("Submission not found"));
        User owner = userRepository.findById(k.getUserId()).orElse(null);
        String url;
        try {
            url = storage.presign(k.getDocumentKey());
        } catch (ApiException e) {
            url = null; // the reviewer sees the details and can retry the photo
        }
        return item(k, owner, fullNameOf(owner), url);
    }

    @Transactional
    public KycReviewItem approve(Long reviewerId, Long id) {
        KycSubmission k = lockForReview(reviewerId, id);
        k.approve(reviewerId);
        User owner = userRepository.findById(k.getUserId()).orElseThrow(() -> new NotFoundException("User not found"));
        owner.setVerificationStatus(User.VerificationStatus.VERIFIED);
        log.info("KYC submission {} approved by user {}", id, reviewerId);
        notifyAfterCommit(owner.getEmail(), true, null);
        return item(k, owner, fullNameOf(owner), null);
    }

    @Transactional
    public KycReviewItem reject(Long reviewerId, Long id, String reason) {
        KycSubmission k = lockForReview(reviewerId, id);
        k.reject(reviewerId, reason.trim());
        User owner = userRepository.findById(k.getUserId()).orElseThrow(() -> new NotFoundException("User not found"));
        log.info("KYC submission {} rejected by user {}", id, reviewerId);
        notifyAfterCommit(owner.getEmail(), false, k.getRejectionReason());
        return item(k, owner, fullNameOf(owner), null);
    }

    private KycSubmission lockForReview(Long reviewerId, Long id) {
        KycSubmission k = submissions.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Submission not found"));
        if (k.getUserId().equals(reviewerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "KYC_OWN_SUBMISSION", "You cannot review your own document");
        }
        if (k.getStatus() != KycStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "KYC_ALREADY_REVIEWED", "This submission was already reviewed");
        }
        return k;
    }

    // after commit: no row lock held during SMTP, and no mail for a decision that rolled back
    private void notifyAfterCommit(String email, boolean approved, String reason) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    emailService.sendKycDecision(email, approved, reason);
                } catch (RuntimeException e) {
                    // the decision stands; the user also sees it in the app
                    log.warn("Could not send the KYC decision mail: {}", e.getClass().getSimpleName());
                }
            }
        });
    }

    private String fullNameOf(User owner) {
        return owner == null ? null : profileRepository.findByUserId(owner.getId())
                .map(EditProfile::getFullName).filter(n -> n != null && !n.isBlank()).orElse(null);
    }

    // profileName: the name from the saved profile, if any; the account's own name otherwise
    private KycReviewItem item(KycSubmission k, User owner, String profileName, String documentUrl) {
        String fullName = profileName != null ? profileName : owner == null ? null : owner.getFullname();
        return new KycReviewItem(k.getId(), k.getUserId(), owner == null ? null : owner.getPublicUsername(), fullName,
                owner == null ? null : owner.getEmail(), k.getDocumentType(), k.getStatus(), k.getSubmittedAt(),
                k.getReviewedAt(), k.getRejectionReason(), documentUrl);
    }
}
