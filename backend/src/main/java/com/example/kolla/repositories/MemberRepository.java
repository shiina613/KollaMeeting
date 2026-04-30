package com.example.kolla.repositories;

import com.example.kolla.models.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Member entities.
 * Requirements: 3.9
 */
@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findByMeetingId(Long meetingId);

    Optional<Member> findByMeetingIdAndUserId(Long meetingId, Long userId);

    boolean existsByMeetingIdAndUserId(Long meetingId, Long userId);

    void deleteByMeetingIdAndUserId(Long meetingId, Long userId);

    /**
     * Returns true if the user is a member of the given meeting.
     * Requirements: 3.9
     */
    @Query("SELECT COUNT(m) > 0 FROM Member m WHERE m.meeting.id = :meetingId AND m.user.id = :userId")
    boolean isMember(@Param("meetingId") Long meetingId, @Param("userId") Long userId);
}
