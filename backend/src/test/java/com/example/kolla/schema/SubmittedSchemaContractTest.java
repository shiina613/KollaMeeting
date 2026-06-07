package com.example.kolla.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SubmittedSchemaContractTest {

    private static final Set<String> WORD_TABLES = Set.of(
            "department",
            "room",
            "user",
            "meeting",
            "member",
            "document",
            "meeting_message");

    private static final Set<String> RUNTIME_TABLES = Set.of(
            "minutes",
            "recording",
            "transcription_job",
            "transcription_segment",
            "attendance_log",
            "participant_session",
            "notification",
            "speaking_permission",
            "storage_log",
            "raise_hand_request");

    @Test
    void versionedMigrationsCreateOnlyTheSevenSubmittedWordTables() throws IOException {
        Path migrationsDir = Path.of("src/main/resources/db/migration");
        Pattern createTable = Pattern.compile(
                "(?i)\\bCREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([A-Za-z0-9_]+)`?");
        Set<String> createdTables = new TreeSet<>();
        Set<String> runtimeDdlTables = new TreeSet<>();

        try (var paths = Files.list(migrationsDir)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql")).toList()) {
                String sql = Files.readString(path, StandardCharsets.UTF_8);
                Matcher matcher = createTable.matcher(sql);
                while (matcher.find()) {
                    createdTables.add(matcher.group(1).toLowerCase());
                }
                for (String runtimeTable : RUNTIME_TABLES) {
                    Pattern runtimeDdl = Pattern.compile(
                            "(?is)\\b(?:CREATE|ALTER|DROP)\\s+TABLE\\s+(?:IF\\s+(?:NOT\\s+)?EXISTS\\s+)?`?"
                                    + runtimeTable
                                    + "`?\\b|\\bREFERENCES\\s+`?"
                                    + runtimeTable
                                    + "`?\\b");
                    if (runtimeDdl.matcher(sql).find()) {
                        runtimeDdlTables.add(runtimeTable);
                    }
                }
            }
        }

        assertThat(createdTables).containsExactlyInAnyOrderElementsOf(WORD_TABLES);
        assertThat(runtimeDdlTables).isEmpty();
    }

    @Test
    void submittedWordRequiredColumnsStayRequiredInMigration() throws IOException {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V1__initial_schema.sql"), StandardCharsets.UTF_8);

        assertColumnContains(sql, "department", "DepartmentCode", "NOT NULL");
        assertColumnContains(sql, "department", "DepartmentCode", "UNIQUE");
        assertColumnContains(sql, "room", "RoomCode", "NOT NULL");
        assertColumnContains(sql, "user", "Department_id", "NOT NULL");
        assertColumnContains(sql, "meeting", "DepartmentId", "NOT NULL");
        assertColumnContains(sql, "meeting", "Room_id", "NOT NULL");
        assertColumnContains(sql, "meeting", "MeetingCode", "NOT NULL");
        assertColumnContains(sql, "document", "Content", "TEXT");
    }

    @Test
    void submittedWordTablesKeepOnlySubmittedWordColumnsInMigration() throws IOException {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V1__initial_schema.sql"), StandardCharsets.UTF_8);

        assertTableColumns(sql, "department", "id", "DepartmentCode", "Name");
        assertTableColumns(sql, "room", "id", "RoomCode", "RoomName");
        assertTableColumns(sql, "user",
                "id", "Department_id", "EmployeeCode", "Password", "Name", "Dob", "PhoneNumber",
                "Degree", "Identification", "Address", "Email", "BankName", "BankNumber", "Img", "Role");
        assertTableColumns(sql, "meeting",
                "id", "MeetingCode", "DepartmentId", "Room_id", "Name", "StartTime", "Endtime", "Status");
        assertTableColumns(sql, "member", "id", "Meeting_id", "User_id", "MeetingRole");
        assertTableColumns(sql, "document", "id", "Meeting_id", "User_id", "Name", "Content");
        assertTableColumns(sql, "meeting_message", "id", "Member_id", "Content", "CreateTime");
    }

    @Test
    void runtimeControlledCodesAndMembershipDuplicatesDoNotUseDbUniqueConstraints() throws IOException {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V1__initial_schema.sql"), StandardCharsets.UTF_8);

        assertColumnDoesNotContain(sql, "room", "RoomCode", "UNIQUE");
        assertColumnDoesNotContain(sql, "meeting", "MeetingCode", "UNIQUE");
        assertThat(sql).doesNotContain("uk_member_meeting_user");
        assertThat(sql).doesNotContain("UNIQUE KEY uk_member_meeting_user");
    }

    @Test
    void submittedWordRequiredColumnsStayRequiredInJpaMappings() throws IOException {
        assertThat(Files.readString(Path.of("src/main/java/com/example/kolla/models/Department.java"), StandardCharsets.UTF_8))
                .contains("@Column(name = \"DepartmentCode\", nullable = false");
        assertThat(Files.readString(Path.of("src/main/java/com/example/kolla/models/Room.java"), StandardCharsets.UTF_8))
                .contains("@Column(name = \"RoomCode\", nullable = false");
        assertThat(Files.readString(Path.of("src/main/java/com/example/kolla/models/User.java"), StandardCharsets.UTF_8))
                .contains("@Column(name = \"Department_id\", nullable = false");
        String meetingSource = Files.readString(Path.of("src/main/java/com/example/kolla/models/Meeting.java"), StandardCharsets.UTF_8);
        assertThat(meetingSource).contains("@Column(name = \"DepartmentId\", nullable = false");
        assertThat(meetingSource).contains("@JoinColumn(name = \"Room_id\", nullable = false");
        assertThat(Files.readString(Path.of("src/main/java/com/example/kolla/models/Document.java"), StandardCharsets.UTF_8))
                .contains("@Column(name = \"Content\", nullable = false, columnDefinition = \"TEXT\"");
    }

    @Test
    void submittedWordJpaMappingsDoNotMapRemovedDatabaseColumns() throws IOException {
        assertThat(Files.readString(Path.of("src/main/java/com/example/kolla/models/Department.java"), StandardCharsets.UTF_8))
                .doesNotContain("@Column(name = \"description\"")
                .doesNotContain("@Column(name = \"created_at\"")
                .doesNotContain("@Column(name = \"updated_at\"");
        assertThat(Files.readString(Path.of("src/main/java/com/example/kolla/models/Room.java"), StandardCharsets.UTF_8))
                .doesNotContain("unique = true")
                .doesNotContain("@Column(name = \"capacity\"")
                .doesNotContain("@JoinColumn(name = \"Department_id\"")
                .doesNotContain("@Column(name = \"created_at\"")
                .doesNotContain("@Column(name = \"updated_at\"");
        assertThat(Files.readString(Path.of("src/main/java/com/example/kolla/models/User.java"), StandardCharsets.UTF_8))
                .doesNotContain("@Column(name = \"is_active\"")
                .doesNotContain("@Column(name = \"created_at\"")
                .doesNotContain("@Column(name = \"updated_at\"");
        assertThat(Files.readString(Path.of("src/main/java/com/example/kolla/models/Meeting.java"), StandardCharsets.UTF_8))
                .doesNotContain("unique = true")
                .doesNotContain("@Column(name = \"description\"")
                .doesNotContain("@JoinColumn(name = \"creator_id\"")
                .doesNotContain("@JoinColumn(name = \"host_user_id\"")
                .doesNotContain("@JoinColumn(name = \"secretary_user_id\"")
                .doesNotContain("@Column(name = \"mode\"")
                .doesNotContain("@Column(name = \"transcription_priority\"")
                .doesNotContain("@Column(name = \"activated_at\"")
                .doesNotContain("@Column(name = \"ended_at\"")
                .doesNotContain("@Column(name = \"waiting_timeout_at\"")
                .doesNotContain("@Column(name = \"created_at\"")
                .doesNotContain("@Column(name = \"updated_at\"");
        assertThat(Files.readString(Path.of("src/main/java/com/example/kolla/models/Member.java"), StandardCharsets.UTF_8))
                .doesNotContain("uniqueConstraints")
                .doesNotContain("@Column(name = \"added_at\"");
        assertThat(Files.readString(Path.of("src/main/java/com/example/kolla/models/Document.java"), StandardCharsets.UTF_8))
                .doesNotContain("@Column(name = \"file_size\"")
                .doesNotContain("@Column(name = \"file_type\"")
                .doesNotContain("@Column(name = \"uploaded_at\"");
    }

    @Test
    void submittedWordRequiredFieldsStayRequiredInCreateDtos() throws IOException {
        assertThat(Files.readString(Path.of("src/main/java/com/example/kolla/dto/CreateDepartmentRequest.java"), StandardCharsets.UTF_8))
                .contains("@NotBlank(message = \"Department code is required\")")
                .contains("private String departmentCode;");
        assertThat(Files.readString(Path.of("src/main/java/com/example/kolla/dto/CreateRoomRequest.java"), StandardCharsets.UTF_8))
                .contains("@NotBlank(message = \"Room code is required\")")
                .contains("private String roomCode;");
        assertThat(Files.readString(Path.of("src/main/java/com/example/kolla/dto/CreateUserRequest.java"), StandardCharsets.UTF_8))
                .contains("@NotNull(message = \"Department ID is required\")")
                .contains("private Long departmentId;");
        String createMeetingRequest = Files.readString(
                Path.of("src/main/java/com/example/kolla/dto/CreateMeetingRequest.java"), StandardCharsets.UTF_8);
        assertThat(createMeetingRequest)
                .contains("@NotNull(message = \"Room ID is required\")")
                .contains("@NotNull(message = \"Department ID is required\")");
    }

    @Test
    void jpaEntitiesMapOnlyTheSevenSubmittedWordTables() throws IOException {
        Path modelsDir = Path.of("src/main/java/com/example/kolla/models");
        Pattern tableAnnotation = Pattern.compile("@Table\\s*\\(\\s*name\\s*=\\s*\"([^\"]+)\"");
        Set<String> mappedTables = new TreeSet<>();

        try (var paths = Files.list(modelsDir)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".java")).toList()) {
                String javaSource = Files.readString(path, StandardCharsets.UTF_8);
                Matcher matcher = tableAnnotation.matcher(javaSource);
                if (matcher.find()) {
                    mappedTables.add(matcher.group(1).toLowerCase());
                }
            }
        }

        assertThat(mappedTables).containsExactlyInAnyOrderElementsOf(WORD_TABLES);
    }

    @Test
    void runtimeTablesDoNotHaveJpaRepositoriesBoundToDatabase() throws IOException {
        Path repositoriesDir = Path.of("src/main/java/com/example/kolla/repositories");
        Set<String> jpaRuntimeRepositories = new TreeSet<>();

        try (var paths = Files.list(repositoriesDir)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith("Repository.java")).toList()) {
                String repositoryName = path.getFileName().toString();
                String javaSource = Files.readString(path, StandardCharsets.UTF_8);
                boolean runtimeRepository = repositoryName.matches(
                        "(Minutes|Recording|TranscriptionJob|TranscriptionSegment|AttendanceLog|ParticipantSession|Notification|SpeakingPermission|StorageLog)Repository\\.java");
                if (runtimeRepository && javaSource.contains("JpaRepository")) {
                    jpaRuntimeRepositories.add(repositoryName);
                }
            }
        }

        assertThat(jpaRuntimeRepositories).isEmpty();
    }

    private static void assertColumnContains(String sql, String table, String column, String expectedFragment) {
        Pattern tablePattern = Pattern.compile("(?is)CREATE\\s+TABLE\\s+`?" + table + "`?\\s*\\((.*?)\\)\\s*ENGINE");
        Matcher tableMatcher = tablePattern.matcher(sql);
        assertThat(tableMatcher.find()).as("table %s exists", table).isTrue();

        Pattern columnPattern = Pattern.compile("(?im)^\\s*`?" + column + "`?\\s+([^,]+)");
        Matcher columnMatcher = columnPattern.matcher(tableMatcher.group(1));
        assertThat(columnMatcher.find()).as("column %s.%s exists", table, column).isTrue();
        assertThat(columnMatcher.group(1).toUpperCase()).contains(expectedFragment);
    }

    private static void assertColumnDoesNotContain(String sql, String table, String column, String unexpectedFragment) {
        Pattern tablePattern = Pattern.compile("(?is)CREATE\\s+TABLE\\s+`?" + table + "`?\\s*\\((.*?)\\)\\s*ENGINE");
        Matcher tableMatcher = tablePattern.matcher(sql);
        assertThat(tableMatcher.find()).as("table %s exists", table).isTrue();

        Pattern columnPattern = Pattern.compile("(?im)^\\s*`?" + column + "`?\\s+([^,]+)");
        Matcher columnMatcher = columnPattern.matcher(tableMatcher.group(1));
        assertThat(columnMatcher.find()).as("column %s.%s exists", table, column).isTrue();
        assertThat(columnMatcher.group(1).toUpperCase()).doesNotContain(unexpectedFragment);
    }

    private static void assertTableColumns(String sql, String table, String... expectedColumns) {
        Pattern tablePattern = Pattern.compile("(?is)CREATE\\s+TABLE\\s+`?" + table + "`?\\s*\\((.*?)\\)\\s*ENGINE");
        Matcher tableMatcher = tablePattern.matcher(sql);
        assertThat(tableMatcher.find()).as("table %s exists", table).isTrue();

        Pattern columnPattern = Pattern.compile("(?im)^\\s*`?([A-Za-z_][A-Za-z0-9_]*)`?\\s+(?:BIGINT|VARCHAR|TEXT|DATE|DATETIME|INT|TINYINT|ENUM)");
        Matcher columnMatcher = columnPattern.matcher(tableMatcher.group(1));
        Set<String> actualColumns = new TreeSet<>();
        while (columnMatcher.find()) {
            actualColumns.add(columnMatcher.group(1));
        }

        assertThat(actualColumns).containsExactlyInAnyOrder(expectedColumns);
    }
}
