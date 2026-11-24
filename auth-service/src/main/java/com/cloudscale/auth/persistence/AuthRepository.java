package com.cloudscale.auth.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuthRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public AuthRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertOrganization(UUID id, String name, String slug, Instant now) {
        jdbc.update("""
                INSERT INTO organizations (id, name, slug, created_at, updated_at)
                VALUES (:id, :name, :slug, :createdAt, :updatedAt)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", name)
                .addValue("slug", slug)
                .addValue("createdAt", Timestamp.from(now))
                .addValue("updatedAt", Timestamp.from(now)));
    }

    public void insertUser(UUID id, String email, String passwordHash, String fullName, Instant now) {
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, full_name, created_at, updated_at)
                VALUES (:id, :email, :passwordHash, :fullName, :createdAt, :updatedAt)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("email", email)
                .addValue("passwordHash", passwordHash)
                .addValue("fullName", fullName)
                .addValue("createdAt", Timestamp.from(now))
                .addValue("updatedAt", Timestamp.from(now)));
    }

    public void insertMembership(UUID id, UUID organizationId, UUID userId, String role, Instant now) {
        jdbc.update("""
                INSERT INTO memberships (id, organization_id, user_id, role, created_at, updated_at)
                VALUES (:id, :organizationId, :userId, :role, :createdAt, :updatedAt)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("organizationId", organizationId)
                .addValue("userId", userId)
                .addValue("role", role)
                .addValue("createdAt", Timestamp.from(now))
                .addValue("updatedAt", Timestamp.from(now)));
    }

    public Optional<AuthAccount> findByEmailAndOrganizationSlug(String email, String organizationSlug) {
        return jdbc.query("""
                SELECT u.id AS user_id, o.id AS organization_id, o.slug AS organization_slug,
                       o.name AS organization_name, u.email, u.password_hash, m.role
                FROM users u
                JOIN memberships m ON m.user_id = u.id
                JOIN organizations o ON o.id = m.organization_id
                WHERE u.email = :email AND o.slug = :organizationSlug
                """, new MapSqlParameterSource()
                        .addValue("email", email)
                        .addValue("organizationSlug", organizationSlug),
                (rs, rowNum) -> new AuthAccount(
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("organization_id", UUID.class),
                        rs.getString("organization_slug"),
                        rs.getString("organization_name"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getString("role")))
                .stream().findFirst();
    }

    public record AuthAccount(
            UUID userId,
            UUID organizationId,
            String organizationSlug,
            String organizationName,
            String email,
            String passwordHash,
            String role
    ) {
    }
}
