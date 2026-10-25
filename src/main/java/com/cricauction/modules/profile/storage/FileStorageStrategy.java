package com.cricauction.modules.profile.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageStrategy {

    String uploadFile(MultipartFile file, String subDirectory);

    void deleteFile(String fileUrl);

    boolean supports(String storageType);
}
