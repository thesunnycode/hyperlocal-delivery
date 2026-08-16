package com.hyperlocal.delivery.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import org.hibernate.annotations.CreationTimestamp;

/**
 * SHA-256 hash of a single-use invite token issued for a delivery agent.
 *
 * <p>An agent's account is created by their owner with a random password that
 * is hashed on the spot and never shown to anyone, so the agent has no way to
 * reach it. An invite is the bridge: a link that proves the holder controls
 * the mailbox the account was created with, and lets them set a password.
 *
 * <p>The plaintext token is returned to the owner (and emailed) exactly once
 * at issuance; only the hash is persisted, the same posture as
 * {@link RefreshToken}.
 *
 * <p>{@code acceptedAt} is the single-use marker rather than a boolean, so the
 * row also records when onboarding finished.
 */
@Entity
@Table(name = "agent_invites")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = { "user" })
public class AgentInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Spent, or past its expiry — either way it cannot be used again. */
    public boolean isUsable(LocalDateTime now) {
        return acceptedAt == null && expiresAt.isAfter(now);
    }
}
