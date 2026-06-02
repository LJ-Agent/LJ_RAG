package com.rag.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/team-roles")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CONFIG:MANAGE')")
public class TeamRoleController {
    // 直接使用 JDBC Template 操作 team_roles / team_role_permissions 表
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        String sql = "SELECT tr.*, (SELECT COUNT(*) FROM team_role_permissions trp WHERE trp.team_role_id=tr.id) AS perm_count FROM team_roles tr ORDER BY tr.sort_order";
        return Result.success(jdbc.queryForList(sql));
    }

    @GetMapping("/{id}/permissions")
    public Result<List<String>> getPermissions(@PathVariable Long id) {
        String sql = "SELECT permission_code FROM team_role_permissions WHERE team_role_id=?";
        return Result.success(jdbc.queryForList(sql, String.class, id));
    }

    @PostMapping
    @Transactional
    public Result<Map<String, Object>> create(@RequestBody Map<String, String> body) {
        String code = body.get("roleCode");
        String name = body.get("roleName");
        String desc = body.getOrDefault("description", "");
        jdbc.update("INSERT IGNORE INTO team_roles (role_code, role_name, description) VALUES (?,?,?)", code, name, desc);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM team_roles WHERE role_code=?", code);
        return Result.success(row);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        if (body.containsKey("roleName")) jdbc.update("UPDATE team_roles SET role_name=? WHERE id=?", body.get("roleName"), id);
        if (body.containsKey("description")) jdbc.update("UPDATE team_roles SET description=? WHERE id=?", body.get("description"), id);
        if (body.containsKey("isDefault")) jdbc.update("UPDATE team_roles SET is_default=? WHERE id=?", Integer.parseInt(body.get("isDefault")), id);
        return Result.success();
    }

    @PutMapping("/{id}/permissions")
    @Transactional
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody Map<String, List<String>> body) {
        List<String> permCodes = body.get("permissionCodes");
        if (permCodes == null) return Result.fail(400, "请提供permissionCodes");
        jdbc.update("DELETE FROM team_role_permissions WHERE team_role_id=?", id);
        for (String pc : permCodes) {
            jdbc.update("INSERT INTO team_role_permissions (team_role_id, permission_code) VALUES (?,?)", id, pc);
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jdbc.update("DELETE FROM team_role_permissions WHERE team_role_id=?", id);
        jdbc.update("DELETE FROM team_roles WHERE id=?", id);
        return Result.success();
    }
}
