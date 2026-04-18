package com.substring.helpdesk.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectCatalogService {

    private final JdbcTemplate jdbcTemplate;

    public ProjectCatalogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> getAvailableProjects() {
        String sql = """
                SELECT DISTINCT trim(metadata->>'project') AS project
                FROM company_docs
                WHERE coalesce(trim(metadata->>'project'), '') <> ''
                ORDER BY project
                """;

        return jdbcTemplate.queryForList(sql, String.class);
    }
}
