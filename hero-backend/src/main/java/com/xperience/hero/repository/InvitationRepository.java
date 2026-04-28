package com.xperience.hero.repository;

import com.xperience.hero.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByInviteToken(String inviteToken);

    List<Invitation> findByEventId(Long eventId);

    boolean existsByEventIdAndEmail(Long eventId, String email);
}
