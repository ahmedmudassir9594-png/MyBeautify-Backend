package com.cosmetics.ecommerce.service;

import com.cosmetics.ecommerce.dto.StoredFile;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    StoredFile storeFile(MultipartFile file);
    Resource loadAsResource(String fileName);
    void deleteFileIfExists(String imagePathOrUrl);
    boolean isLocalUpload(String imagePathOrUrl);
    String getUploadDir();
}

