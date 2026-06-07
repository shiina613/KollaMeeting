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
}
