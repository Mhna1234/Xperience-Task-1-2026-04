package com.xperience.hero.dto;

import java.util.List;

public record InviteResponse(
        List<InvitationDetail> created,
        List<String> duplicates
) {
    public record InvitationDetail(Long invitationId, String email, String inviteToken) {}
}
