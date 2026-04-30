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
    private static final int MAX_FOLDER_DEPTH = 2;

    private final StudyFolderRepository folderRepository;

    public FolderService(StudyFolderRepository folderRepository) {
        this.folderRepository = folderRepository;
    }

    @Transactional(readOnly = true)
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

        if (request.parentId() != null) {
            StudyFolder parent = requireOwned(request.parentId(), user.getId());
            if (parent.getDepth() >= MAX_FOLDER_DEPTH) {
                throw new IllegalArgumentException("Folders support at most 2 levels");
            }
            folder.setParent(parent);
            folder.setDepth(parent.getDepth() + 1);
        }

        folderRepository.save(folder);
        return toResponse(folder);
    }

    public StudyFolder requireOwned(Long folderId, Long userId) {
        return folderRepository.findByIdAndOwnerId(folderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found or access denied"));
    }

    private FolderResponse toResponse(StudyFolder folder) {
        Long parentId = folder.getParent() == null ? null : folder.getParent().getId();
        return new FolderResponse(folder.getId(), folder.getName(), folder.getDescription(), parentId,
                folder.getDepth(), folder.getCreatedAt());
    }
}
