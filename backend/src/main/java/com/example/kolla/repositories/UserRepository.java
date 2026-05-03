package com.example.kolla.repositories;

import com.example.kolla.enums.Role;
import com.example.kolla.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for User entities.
 * Requirements: 11.1, 11.7
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * Returns active users with the given role, ordered by full name.
     * Used to populate host/secretary dropdowns in the meeting form.
     */
    List<User> findByRoleAndIsActiveTrueOrderByFullNameAsc(Role role);

    /**
     * Returns all active users ordered by full name.
     * Used to populate the member picker panel.
     */
    List<User> findByIsActiveTrueOrderByFullNameAsc();

    /**
     * Search active users by full name or username (case-insensitive, partial match).
     * Used to add members to a meeting. Accessible by any authenticated user.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.isActive = true
              AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY u.fullName ASC
            """)
    List<User> searchByNameOrUsername(@Param("q") String query, org.springframework.data.domain.Pageable pageable);

    /**
     * Returns true if the user is a member of any SCHEDULED or ACTIVE meeting.
     * Used to prevent deletion of users with active meeting memberships.
     * Requirements: 11.7
     */
    @Query("""
            SELECT COUNT(m) > 0
            FROM Member m
            JOIN m.meeting mt
            WHERE m.user.id = :userId
              AND mt.status IN (com.example.kolla.enums.MeetingStatus.SCHEDULED,
                                com.example.kolla.enums.MeetingStatus.ACTIVE)
            """)
    boolean hasActiveMeetingMemberships(@Param("userId") Long userId);
}
