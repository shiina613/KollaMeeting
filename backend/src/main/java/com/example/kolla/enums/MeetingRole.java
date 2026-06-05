package com.example.kolla.enums;

/**
 * Role of a user inside a specific meeting.
 *
 * <p>This is separate from the system-wide {@link Role}. A regular employee
 * can be the HOST/chairperson of one meeting while still having system role USER.
 */
public enum MeetingRole {
    HOST,
    SECRETARY,
    REVIEWER,
    COMMITTEE_MEMBER,
    GUEST,
    MEMBER
}
