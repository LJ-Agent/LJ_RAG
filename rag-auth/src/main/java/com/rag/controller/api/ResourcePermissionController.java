package com.rag.controller.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.*;

@RestController
@RequestMapping("/api/resource-permissions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('CONFIG:MANAGE','ROLE:MANAGE')")
public class ResourcePermissionController {
    private final JdbcTemplate jdbc;

    @GetMapping
    public List<Map<String,Object>> list(@RequestParam(defaultValue="tab") String type) {
        return jdbc.queryForList("SELECT * FROM resource_permissions WHERE resource_type=? ORDER BY resource_path", type);
    }

    @GetMapping("/map")
    public Map<String, List<String>> permissionMap() {
        // 返回 {"/api/roles": ["ROLE:MANAGE"], "tab:roles": ["ROLE:MANAGE"], ...}
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT resource_path, permission_code FROM resource_permissions");
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (var row : rows) {
            String path = (String) row.get("resource_path");
            String perm = (String) row.get("permission_code");
            map.computeIfAbsent(path, k -> new ArrayList<>()).add(perm);
        }
        return map;
    }

    @PostMapping
    @Transactional
    public Map<String,Object> create(@RequestBody Map<String,String> body) {
        jdbc.update("INSERT IGNORE INTO resource_permissions (resource_path, resource_type, permission_code, description) VALUES (?,?,?,?)",
            body.get("resourcePath"), body.get("resourceType"), body.get("permissionCode"), body.getOrDefault("description",""));
        return Map.of("success", true);
    }

    @PutMapping("/{id}")
    public Map<String,Object> update(@PathVariable Long id, @RequestBody Map<String,String> body) {
        if(body.containsKey("permissionCode"))
            jdbc.update("UPDATE resource_permissions SET permission_code=? WHERE id=?", body.get("permissionCode"), id);
        if(body.containsKey("description"))
            jdbc.update("UPDATE resource_permissions SET description=? WHERE id=?", body.get("description"), id);
        return Map.of("success", true);
    }

    @DeleteMapping("/{id}")
    public Map<String,Object> delete(@PathVariable Long id) {
        jdbc.update("DELETE FROM resource_permissions WHERE id=?", id);
        return Map.of("success", true);
    }
}
