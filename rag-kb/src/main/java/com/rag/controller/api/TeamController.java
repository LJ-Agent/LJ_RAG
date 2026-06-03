package com.rag.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.context.annotation.Profile;
import com.rag.common.context.UserContext;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.Result;
import com.rag.common.result.ResultCodeEnum;
import com.rag.domain.entity.*;
import com.rag.domain.mapper.*;
import org.springframework.context.annotation.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Profile({"kb", "prod"})
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {
    private final com.rag.domain.mapper.TeamMapper teamMapper;
    private final com.rag.domain.mapper.TeamMemberMapper teamMemberMapper;
    private final com.rag.domain.mapper.TeamKnowledgeBaseMapper teamKbMapper;

    @PostMapping
    public Result<List<Map<String,Object>>> myTeams() {
        Long userId = UserContext.getUserId();
        List<TeamMember> memberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getUserId, userId));
        if (memberships.isEmpty()) return Result.success(List.of());
        List<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).toList();
        List<Team> teams = teamMapper.selectBatchIds(teamIds);
        List<Map<String,Object>> result = new ArrayList<>();
        for (Team t : teams) {
            Map<String,Object> m = new HashMap<>();
            m.put("team", t);
            m.put("role", memberships.stream().filter(mb -> mb.getTeamId().equals(t.getId())).findFirst().map(TeamMember::getRoleCode).orElse("team_viewer"));
            result.add(m);
        }
        return Result.success(result);
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasAuthority('CONFIG:MANAGE')")
    public Result<Team> create(@RequestBody Team team) {
        team.setOwnerId(UserContext.getUserId());
        teamMapper.insert(team);
        TeamMember tm = new TeamMember();
        tm.setTeamId(team.getId()); tm.setUserId(UserContext.getUserId()); tm.setRoleCode("team_owner");
        teamMemberMapper.insert(tm);
        return Result.success(team);
    }

    @PostMapping("/{id}")
    public Result<Team> detail(@PathVariable Long id) { return Result.success(teamMapper.selectById(id)); }

    @PutMapping("/{id}")
    public Result<Team> update(@PathVariable Long id, @RequestBody Team team) {
        Team exist = teamMapper.selectById(id); if (exist==null) throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(),"团队不存在");
        if (team.getName()!=null) exist.setName(team.getName());
        if (team.getDescription()!=null) exist.setDescription(team.getDescription());
        teamMapper.updateById(exist); return Result.success(exist);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Result<Void> delete(@PathVariable Long id) {
        teamMemberMapper.delete(new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId,id));
        teamKbMapper.delete(new LambdaQueryWrapper<TeamKnowledgeBase>().eq(TeamKnowledgeBase::getTeamId,id));
        teamMapper.deleteById(id); return Result.success();
    }

    @PostMapping("/{id}/members")
    public Result<List<TeamMember>> members(@PathVariable Long id) {
        return Result.success(teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId,id)));
    }

    @PostMapping("/{id}/members")
    public Result<TeamMember> addMember(@PathVariable Long id, @RequestBody TeamMember member) {
        member.setTeamId(id);
        TeamMember exist = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId,id).eq(TeamMember::getUserId,member.getUserId()));
        if (exist!=null) throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(),"用户已在团队中");
        teamMemberMapper.insert(member); return Result.success(member);
    }

    @PutMapping("/{id}/members/{userId}")
    public Result<Void> updateMemberRole(@PathVariable Long id, @PathVariable Long userId, @RequestBody Map<String,String> body) {
        TeamMember tm = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId,id).eq(TeamMember::getUserId,userId));
        if (tm==null) throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(),"成员不存在");
        tm.setRoleCode(body.getOrDefault("roleCode","team_viewer"));
        teamMemberMapper.updateById(tm); return Result.success();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        teamMemberMapper.delete(new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId,id).eq(TeamMember::getUserId,userId));
        return Result.success();
    }
}
