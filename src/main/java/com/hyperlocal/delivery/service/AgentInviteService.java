package com.hyperlocal.delivery.service;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hyperlocal.delivery.config.MailProperties;
import com.hyperlocal.delivery.dto.invite.AcceptInviteRequest;
import com.hyperlocal.delivery.dto.invite.InvitePreviewResponse;
import com.hyperlocal.delivery.dto.invite.InviteResponse;
import com.hyperlocal.delivery.exception.AgentNotFoundException;
import com.hyperlocal.delivery.exception.InvalidInviteTokenException;
import com.hyperlocal.delivery.model.AgentInvite;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.model.UserRole;
import com.hyperlocal.delivery.repository.AgentInviteRepository;
import com.hyperlocal.delivery.repository.RefreshTokenRepository;
import com.hyperlocal.delivery.repository.UserRepository;
import com.hyperlocal.delivery.security.JwtUtil;
import com.hyperlocal.delivery.util.TimeUtils;

import lombok.RequiredArgsConstructor;

/**
 * Agent onboarding.
 *
 * <p>The problem this solves: {@link AgentService#create} mints an account
 * with a random password that is hashed immediately and shown to nobody, so
 * the agent has no way to discover the account exists, let alone sign in. An
 * invite is a single-use expiring link that proves the holder controls the
 * mailbox the account was created with and lets them choose a password.
 *
 * <p>Three deliberate choices:
 * <ul>
 *   <li>Issuing an invite retires any earlier one for that agent, so a
 *       forwarded or leaked older link stops working the moment a new one is
 *       sent.</li>
 *   <li>Accepting revokes every refresh token the account holds. Nothing
 *       should still be signed in as an account whose password was just set
 *       by someone proving ownership for the first time.</li>
 *   <li>A failed email is not a failed request. The invite row is committed
 *       and the link is returned either way, and {@code emailed} says which
 *       happened, so the owner console can tell the truth rather than
 *       claiming a message was sent.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AgentInviteService {

    private final AgentInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AgentInviteMailer inviteMailer;
    private final MailProperties mailProperties;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * Issue a fresh invite for one of the caller's own agents.
     *
     * <p>Tenant-scoped on {@code businessId} and restricted to
     * DELIVERY_AGENT rows: an owner cannot mint a password-setting link for
     * another owner, or for someone else's agent.
     */
    @Transactional
    public InviteResponse issue(Long businessId, Long agentId) {
        User agent = userRepository.findById(agentId)
                .filter(u -> u.getRole() == UserRole.DELIVERY_AGENT)
                .filter(u -> u.getBusiness() != null && u.getBusiness().getId().equals(businessId))
                .orElseThrow(() -> new AgentNotFoundException(agentId));

        LocalDateTime now = LocalDateTime.now();
        inviteRepository.expireOutstanding(agent.getId(), now);

        String rawToken = jwtUtil.generateOpaqueToken();
        inviteRepository.save(AgentInvite.builder()
                .user(agent)
                .tokenHash(JwtUtil.sha256Hex(rawToken))
                .expiresAt(now.plusHours(mailProperties.inviteTtlHours()))
                .build());

        String businessName = agent.getBusiness().getName();
        boolean emailed = inviteMailer.sendInviteEmail(
                agent.getEmail(), agent.getFullName(), businessName, rawToken);

        return new InviteResponse(
                MailLinkBuilder.buildInviteLink(mailProperties.inviteLinkBaseUrl(), rawToken),
                TimeUtils.toIso(now.plusHours(mailProperties.inviteTtlHours())),
                emailed);
    }

    /**
     * What the setup screen may show before any password exists. Read-only,
     * and it does not spend the invite — opening the link twice is normal.
     */
    @Transactional(readOnly = true)
    public InvitePreviewResponse preview(String rawToken) {
        AgentInvite invite = usable(rawToken);
        User agent = invite.getUser();
        return new InvitePreviewResponse(
                agent.getFullName(),
                agent.getEmail(),
                agent.getBusiness() != null ? agent.getBusiness().getName() : null,
                TimeUtils.toIso(invite.getExpiresAt()));
    }

    /**
     * Spend the invite and set the password.
     *
     * <p>The row is claimed first, by a conditional UPDATE that only touches
     * an unspent, unexpired invite. If that reports no rows, someone else won
     * the race (or the link was already dead) and nothing is written.
     */
    @Transactional
    public void accept(AcceptInviteRequest req) {
        LocalDateTime now = LocalDateTime.now();
        String hash = JwtUtil.sha256Hex(req.token());

        AgentInvite invite = inviteRepository.findByTokenHash(hash)
                .orElseThrow(InvalidInviteTokenException::new);

        if (inviteRepository.markAccepted(hash, now) == 0) {
            throw new InvalidInviteTokenException();
        }

        User agent = invite.getUser();
        agent.setPasswordHash(passwordEncoder.encode(req.password()));
        agent.setPasswordChangedAt(now);
        userRepository.save(agent);

        // Nothing should stay signed in as an account that has only now been
        // claimed by its owner.
        refreshTokenRepository.deleteByUser_Id(agent.getId());
    }

    private AgentInvite usable(String rawToken) {
        AgentInvite invite = inviteRepository.findByTokenHash(JwtUtil.sha256Hex(rawToken))
                .orElseThrow(InvalidInviteTokenException::new);
        if (!invite.isUsable(LocalDateTime.now())) {
            throw new InvalidInviteTokenException();
        }
        return invite;
    }
}
