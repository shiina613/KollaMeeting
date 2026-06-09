package com.example.kolla.repositories;

import com.example.kolla.enums.Role;
import com.example.kolla.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("SELECT u FROM User u WHERE u.username NOT LIKE 'deleted-user-%'")
    Page<User> findVisibleUsers(Pageable pageable);

    boolean existsByUsername(String username);

    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByIdentification(String identification);

    /**
     * Returns users with the given role, ordered by full name.
     * Used to populate meeting candidate dropdowns.
     */
    List<User> findByRoleOrderByFullNameAsc(Role role);

    @Query("SELECT u FROM User u WHERE u.role = :role AND u.username NOT LIKE 'deleted-user-%' ORDER BY u.fullName ASC")
    List<User> findVisibleByRoleOrderByFullNameAsc(@Param("role") Role role);

    /**
     * Returns all users ordered by full name.
     * Used to populate the member picker panel.
     */
    List<User> findAllByOrderByFullNameAsc();

    @Query("SELECT u FROM User u WHERE u.username NOT LIKE 'deleted-user-%' ORDER BY u.fullName ASC")
    List<User> findAllVisibleByOrderByFullNameAsc();

    /**
     * Search users by full name or username (case-insensitive, partial match).
     * Used to add members to a meeting. Accessible by any authenticated user.
     */
    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(u.employeeCode) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY u.fullName ASC
            """)
    List<User> searchByNameOrUsername(@Param("q") String query, org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT u FROM User u
            WHERE u.username NOT LIKE 'deleted-user-%'
              AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(u.employeeCode) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY u.fullName ASC
            """)
    List<User> searchVisibleByNameOrUsername(@Param("q") String query, Pageable pageable);

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
