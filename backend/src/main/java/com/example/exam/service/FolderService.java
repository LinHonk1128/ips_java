package com.example.exam.service;

import com.example.exam.dto.FolderDtos.CreateFolderRequest;
import com.example.exam.dto.FolderDtos.FolderResponse;
import com.example.exam.dto.FolderDtos.UpdateFolderRequest;
import com.example.exam.model.StudyFolder;
import com.example.exam.model.StudyFile;
import com.example.exam.model.User;
import com.example.exam.repository.KnowledgeChunkRepository;
import com.example.exam.repository.StudyFileRepository;
import com.example.exam.repository.StudyFolderRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FolderService {
    private static final int MAX_FOLDER_DEPTH = 3;

    private final StudyFolderRepository folderRepository;
    private final StudyFileRepository fileRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final ElasticsearchService elasticsearchService;

    public FolderService(StudyFolderRepository folderRepository,
                         StudyFileRepository fileRepository,
                         KnowledgeChunkRepository chunkRepository,
                         ElasticsearchService elasticsearchService) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.chunkRepository = chunkRepository;
        this.elasticsearchService = elasticsearchService;
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
                throw new IllegalArgumentException("Folders support at most 3 levels");
            }
            folder.setParent(parent);
            folder.setDepth(parent.getDepth() + 1);
        }

        folderRepository.save(folder);
        return toResponse(folder);
    }

    @Transactional
    public FolderResponse update(Long folderId, Long userId, UpdateFolderRequest request) {
        StudyFolder folder = requireOwned(folderId, userId);
        folder.setName(request.name());
        folder.setDescription(request.description());
        return toResponse(folder);
    }

    @Transactional
    public void delete(Long folderId, Long userId) throws IOException {
        StudyFolder folder = requireOwned(folderId, userId);
        List<StudyFolder> ownedFolders = folderRepository.findByOwnerIdOrderByCreatedAtDesc(userId);
        Set<Long> folderIds = descendantIds(folder, ownedFolders);
        List<StudyFile> files = fileRepository.findByFolderIdIn(folderIds);

        for (StudyFile file : files) {
            elasticsearchService.deleteByFileId(userId, file.getId());
        }
        chunkRepository.deleteByFolderIdIn(folderIds);
        fileRepository.deleteAll(files);
        fileRepository.flush();
        folderRepository.deleteAll(ownedFolders.stream()
                .filter(item -> folderIds.contains(item.getId()))
                .sorted(Comparator.comparingInt(StudyFolder::getDepth).reversed())
                .toList());

        for (StudyFile file : files) {
            Files.deleteIfExists(Path.of(file.getStoredPath()));
        }
    }

    public StudyFolder requireOwned(Long folderId, Long userId) {
        return folderRepository.findByIdAndOwnerId(folderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found or access denied"));
    }

    private Set<Long> descendantIds(StudyFolder root, List<StudyFolder> ownedFolders) {
        Set<Long> ids = new HashSet<>();
        ids.add(root.getId());
        boolean changed;
        do {
            changed = false;
            for (StudyFolder candidate : ownedFolders) {
                StudyFolder parent = candidate.getParent();
                if (parent != null && ids.contains(parent.getId()) && ids.add(candidate.getId())) {
                    changed = true;
                }
            }
        } while (changed);
        return ids;
    }

    private FolderResponse toResponse(StudyFolder folder) {
        Long parentId = folder.getParent() == null ? null : folder.getParent().getId();
        return new FolderResponse(folder.getId(), folder.getName(), folder.getDescription(), parentId,
                folder.getDepth(), folder.getCreatedAt());
    }
}
