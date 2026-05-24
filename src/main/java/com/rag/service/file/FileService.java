package com.rag.service.file;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.service.file.dto.FileQueryDTO;
import com.rag.service.file.dto.FileVO;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    Result<FileVO> upload(MultipartFile file, Long kbId, Long userId, String chunkStrategy);

    Result<Page<FileVO>> list(FileQueryDTO query);

    Result<FileVO> detail(Long id);

    Result<Void> delete(Long id, Long userId);

    void download(Long id, jakarta.servlet.http.HttpServletResponse response);

    void getContent(Long id, jakarta.servlet.http.HttpServletResponse response);

    Result<String> getPresignedUrl(Long id);
}
