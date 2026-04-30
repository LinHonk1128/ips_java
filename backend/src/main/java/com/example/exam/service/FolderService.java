package com.example.exam.service;

import com.example.exam.dto.FolderDtos.CreateFolderRequest;
import com.example.exam.dto.FolderDtos.FolderResponse;
import com.example.exam.model.StudyFolder;
import com.example.exam.model.User;
import com.example.exam.repository.StudyFolderRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FolderService {
    private final StudyFolderRepository folderRepository;

    public FolderService(StudyFolderRepository folderRepository) {
        this.folderRepository = folderRepository;
    }

    public List<FolderResponse> list(User user) {
        return folderRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FolderResponse create(User user, CreateFolderRequest request) {
        StudyFolder folder = new StudyFolder();
        folder.setOwner(user);
        folder.setName(request.name());
        folder.setDescription(request.description());
        folderRepository.save(folder);
        return toResponse(folder);
    }

    public StudyFolder requireOwned(Long folderId, Long userId) {
        return folderRepository.findByIdAndOwnerId(folderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found or access denied"));
    }

    private FolderResponse toResponse(StudyFolder folder) {
        return new FolderResponse(folder.getId(), folder.getName(), folder.getDescription(), folder.getCreatedAt());
    }
}
