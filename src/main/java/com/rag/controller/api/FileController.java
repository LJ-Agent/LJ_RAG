package com.rag.controller.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.controller.interceptor.JwtAuthInterceptor;
import com.rag.service.file.FileService;
import com.rag.service.file.dto.FileQueryDTO;
import com.rag.service.file.dto.FileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "文件管理", description = "文件上传、下载、删除、查询")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public Result<FileVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("kbId") Long kbId,
            @RequestParam(value = "chunkStrategy", required = false, defaultValue = "semantic") String chunkStrategy) {
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return fileService.upload(file, kbId, userId, chunkStrategy);
    }

    @Operation(summary = "查询文档列表")
    @GetMapping
    public Result<Page<FileVO>> list(FileQueryDTO query) {
        return fileService.list(query);
    }

    @Operation(summary = "查询文档详情")
    @GetMapping("/{id}")
    public Result<FileVO> detail(@PathVariable Long id) {
        return fileService.detail(id);
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return fileService.delete(id, userId);
    }

    @Operation(summary = "下载原始文件")
    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) {
        fileService.download(id, response);
    }

    @Operation(summary = "获取文档内容（清洗后的markdown或原始文本）")
    @GetMapping("/{id}/content")
    public void getContent(@PathVariable Long id, HttpServletResponse response) {
        fileService.getContent(id, response);
    }

    @Operation(summary = "获取原始文件（inline预览，带JWT鉴权）")
    @GetMapping("/{id}/raw")
    public void raw(@PathVariable Long id, HttpServletResponse response) {
        fileService.raw(id, response);
    }
}
