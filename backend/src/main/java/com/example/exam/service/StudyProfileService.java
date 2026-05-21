package com.example.exam.service;

import com.example.exam.dto.StudyProfileDtos.OnboardingRequest;
import com.example.exam.dto.StudyProfileDtos.StudyProfileResponse;
import com.example.exam.dto.StudyProfileDtos.SubjectFolderResponse;
import com.example.exam.dto.StudyProfileDtos.UpdateStudyProfileRequest;
import com.example.exam.model.StudyFolder;
import com.example.exam.model.User;
import com.example.exam.model.UserStudyProfile;
import com.example.exam.repository.StudyFolderRepository;
import com.example.exam.repository.UserRepository;
import com.example.exam.repository.UserStudyProfileRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyProfileService {
    private final UserStudyProfileRepository profileRepository;
    private final StudyFolderRepository folderRepository;
    private final UserRepository userRepository;

    public StudyProfileService(UserStudyProfileRepository profileRepository,
                               StudyFolderRepository folderRepository,
                               UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public StudyProfileResponse getOrCreate(Long userId) {
        UserStudyProfile profile = profileRepository.findByOwnerId(userId)
                .orElseGet(() -> createLegacyProfile(userId));
        return toResponse(userId, profile);
    }

    @Transactional
    public StudyProfileResponse onboard(Long userId, OnboardingRequest request) {
        if (profileRepository.existsByOwnerId(userId)) {
            UserStudyProfile existing = profileRepository.findByOwnerId(userId).orElseThrow();
            if (existing.isOnboarded()) {
                throw new IllegalArgumentException("Study profile is already initialized");
            }
        }
        User owner = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        List<String> subjects = normalizeSubjects(request.subjects());
        if (subjects.isEmpty()) {
            throw new IllegalArgumentException("Please set at least one subject");
        }

        UserStudyProfile profile = profileRepository.findByOwnerId(userId).orElseGet(() -> {
            UserStudyProfile created = new UserStudyProfile();
            created.setOwner(owner);
            return created;
        });
        profile.setExamDate(request.examDate());
        profile.setOnboarded(true);
        profile.setSubjectCount(subjects.size());
        profileRepository.save(profile);

        List<StudyFolder> roots = folderRepository.findByOwnerIdAndParentIsNullOrderByCreatedAtAsc(userId);
        if (roots.isEmpty()) {
            for (int i = 0; i < subjects.size(); i++) {
                StudyFolder folder = new StudyFolder();
                folder.setOwner(owner);
                folder.setName(subjects.get(i));
                folder.setDepth(1);
                folder.setSubjectFolder(true);
                folder.setSubjectOrder(i);
                folderRepository.save(folder);
            }
        } else {
            for (int i = 0; i < roots.size(); i++) {
                StudyFolder folder = roots.get(i);
                folder.setSubjectFolder(true);
                folder.setSubjectOrder(i);
            }
        }

        return toResponse(userId, profile);
    }

    @Transactional
    public StudyProfileResponse update(Long userId, UpdateStudyProfileRequest request) {
        UserStudyProfile profile = profileRepository.findByOwnerId(userId)
                .orElseGet(() -> createLegacyProfile(userId));
        profile.setExamDate(request.examDate());
        List<String> subjects = normalizeSubjects(request.subjects());
        if (!subjects.isEmpty()) {
            syncSubjectFolders(userId, subjects);
            profile.setSubjectCount(subjects.size());
            profile.setOnboarded(true);
        }
        return toResponse(userId, profile);
    }

    private void syncSubjectFolders(Long userId, List<String> subjects) {
        User owner = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        List<StudyFolder> subjectRoots = folderRepository.findByOwnerIdAndSubjectFolderTrueOrderBySubjectOrderAscCreatedAtAsc(userId)
                .stream()
                .filter(folder -> folder.getParent() == null)
                .toList();
        for (int i = 0; i < subjects.size(); i++) {
            StudyFolder folder;
            if (i < subjectRoots.size()) {
                folder = subjectRoots.get(i);
            } else {
                folder = new StudyFolder();
                folder.setOwner(owner);
                folder.setDepth(1);
            }
            folder.setName(subjects.get(i));
            folder.setSubjectFolder(true);
            folder.setSubjectOrder(i);
            folderRepository.save(folder);
        }
        for (int i = subjects.size(); i < subjectRoots.size(); i++) {
            StudyFolder folder = subjectRoots.get(i);
            folder.setSubjectFolder(false);
            folder.setSubjectOrder(0);
        }
    }

    private UserStudyProfile createLegacyProfile(Long userId) {
        User owner = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        UserStudyProfile profile = new UserStudyProfile();
        profile.setOwner(owner);
        List<StudyFolder> roots = folderRepository.findByOwnerIdAndParentIsNullOrderByCreatedAtAsc(userId);
        if (!roots.isEmpty()) {
            for (int i = 0; i < roots.size(); i++) {
                StudyFolder folder = roots.get(i);
                folder.setSubjectFolder(true);
                folder.setSubjectOrder(i);
            }
            profile.setOnboarded(true);
            profile.setSubjectCount(roots.size());
        }
        return profileRepository.save(profile);
    }

    private List<String> normalizeSubjects(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = value == null ? "" : value.trim();
            if (!normalized.isBlank()) {
                names.add(normalized);
            }
        }
        return new ArrayList<>(names);
    }

    private StudyProfileResponse toResponse(Long userId, UserStudyProfile profile) {
        List<SubjectFolderResponse> subjects = folderRepository
                .findByOwnerIdAndSubjectFolderTrueOrderBySubjectOrderAscCreatedAtAsc(userId)
                .stream()
                .filter(folder -> folder.getParent() == null)
                .map(folder -> new SubjectFolderResponse(folder.getId(), folder.getName(), folder.getSubjectOrder()))
                .toList();
        return new StudyProfileResponse(profile.isOnboarded(), profile.getExamDate(), profile.getSubjectCount(), subjects);
    }
}
