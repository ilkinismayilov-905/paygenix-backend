package com.example.paygenix.admin;

import com.example.paygenix.entity.ProblemReport;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.ReportStatus;
import com.example.paygenix.enums.Role;
import com.example.paygenix.exceptions.ApiException;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.repository.EditProfileRepository;
import com.example.paygenix.repository.ProblemReportRepository;
import com.example.paygenix.repository.UserRepository;
import com.example.paygenix.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Admin tools: find users and lock or unlock them; work through problem reports. Every action is logged. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository users;
    private final EditProfileRepository profiles;
    private final ProblemReportRepository reports;
    private final SessionService sessionService;

    @Transactional(readOnly = true)
    public AdminPage<AdminUserItem> users(String query, int page, int size) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        // LIKE wildcards in the search are literal characters, not patterns
        String pattern = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Slice<User> slice = users.search(pattern, PageRequest.of(page, size, Sort.by("id").descending()));
        return new AdminPage<>(slice.map(this::item).toList(), page, size, slice.hasNext());
    }

    @Transactional
    public AdminUserItem setLocked(User admin, Long userId, boolean locked) {
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getId().equals(admin.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_LOCK_SELF", "You cannot lock your own account");
        }
        if (user.getRoles().contains(Role.ADMIN_ROLE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_TARGET", "Admins are locked and unlocked in the database only");
        }
        user.setStatus(locked ? User.Status.LOCKED : User.Status.ACTIVE);
        if (locked) {
            sessionService.revokeAll(user.getId()); // signed out everywhere at once
        }
        log.info("User {} {} by admin {}", user.getId(), locked ? "locked" : "unlocked", admin.getId());
        return item(user);
    }

    @Transactional(readOnly = true)
    public AdminPage<AdminReportItem> reports(ReportStatus status, int page, int size) {
        Slice<ProblemReport> slice = reports.findByStatus(status, PageRequest.of(page, size,
                Sort.by("createdAt").descending().and(Sort.by("id").descending())));
        Map<Long, User> owners = users.findAllById(slice.map(ProblemReport::getUserId).toSet()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return new AdminPage<>(slice.map(r -> report(r, owners.get(r.getUserId()))).toList(), page, size, slice.hasNext());
    }

    @Transactional
    public AdminReportItem setReportStatus(User admin, Long id, ReportStatus status) {
        ProblemReport r = reports.findById(id).orElseThrow(() -> new NotFoundException("Report not found"));
        r.setStatus(status);
        log.info("Report {} set to {} by admin {}", id, status, admin.getId());
        return report(r, users.findById(r.getUserId()).orElse(null));
    }

    private AdminUserItem item(User u) {
        String name = profiles.findByUserId(u.getId()).map(p -> p.getFullName()).filter(n -> n != null && !n.isBlank())
                .orElse(u.getFullname());
        return new AdminUserItem(u.getId(), u.getPublicUsername(), u.getEmail(), name,
                u.getStatus() == null ? User.Status.ACTIVE.name() : u.getStatus().name(),
                u.getVerificationStatus() == User.VerificationStatus.VERIFIED,
                u.getRoles().stream().map(Enum::name).sorted(Comparator.naturalOrder()).toList(), u.getCreatedTime());
    }

    private static AdminReportItem report(ProblemReport r, User owner) {
        return new AdminReportItem(r.getId(), r.getUserId(), owner == null ? null : owner.getPublicUsername(),
                owner == null ? null : owner.getEmail(), r.getCategory(), r.getMessage(), r.getReferenceId(), r.getStatus(),
                r.getCreatedAt());
    }
}
