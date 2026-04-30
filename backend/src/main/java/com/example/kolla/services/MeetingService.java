package com.example.kolla.services;

import com.example.kolla.dto.AddMemberRequest;
import com.example.kolla.dto.CreateMeetingRequest;
import com.example.kolla.dto.UpdateMeetingRequest;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.models.User;
import com.example.kolla.responses.MeetingResponse;
import com.example.kolla.responses.MemberResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Meeting management service interface.
 * Requirements: 3.1–3.12
 */
public interface MeetingService {

    /**
     * Create a new meeting.
     * Validates host/secretary roles and room scheduling conflicts.
     * Requirements: 3.1, 3.2, 3.8, 3.12
     */
    MeetingResponse createMeeting(CreateMeetingRequest request, User creator);

    /**
     * Get a meeting by ID.
     * Requirements: 3.7
     */
    MeetingResponse getMeetingById(Long id, User requester);

    /**
     * List meetings with optional filters (paginated).
     * Requirements: 3.4, 13.2
     */
    Page<MeetingResponse> listMeetings(MeetingStatus status,
                                       Long roomId,
                                       Long creatorId,
                                       LocalDateTime startFrom,
                                       LocalDateTime startTo,
                                       Pageable pageable,
                                       User requester);

    /**
     * Update a meeting.
     * Validates permissions and room scheduling conflicts.
     * Requirements: 3.5, 3.12
     */
    MeetingResponse updateMeeting(Long id, UpdateMeetingRequest request, User requester);

    /**
     * Delete a meeting and cascade-delete related records.
     * Requirements: 3.6
     */
    void deleteMeeting(Long id, User requester);

    /**
     * List members of a meeting.
     * Requirements: 3.9
     */
    List<MemberResponse> listMembers(Long meetingId, User requester);

    /**
     * Add a member to a meeting.
     * Requirements: 3.9, 10.3
     */
    MemberResponse addMember(Long meetingId, AddMemberRequest request, User requester);

    /**
     * Remove a member from a meeting.
     * Requirements: 3.9
     */
    void removeMember(Long meetingId, Long userId, User requester);

    /**
     * Check if a user is a member of a meeting.
     * Requirements: 3.9
     */
    boolean isMember(Long meetingId, Long userId);
}
