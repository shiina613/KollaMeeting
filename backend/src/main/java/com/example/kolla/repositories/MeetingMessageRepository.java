package com.example.kolla.repositories;

import com.example.kolla.models.MeetingMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingMessageRepository extends JpaRepository<MeetingMessage, Long> {

    List<MeetingMessage> findByMemberMeetingIdOrderByCreateTimeAscIdAsc(Long meetingId);
}
