package com.example.exam.service;

import com.example.exam.dto.StudyPlanDtos.StudyPlanItemRequest;
import com.example.exam.dto.StudyPlanDtos.StudyPlanItemResponse;
import com.example.exam.model.StudyPlanItem;
import com.example.exam.model.StudyPlanItemType;
import com.example.exam.model.StudyPlanPriority;
import com.example.exam.model.StudyPlanSource;
import com.example.exam.model.StudyPlanStatus;
import com.example.exam.model.User;
import com.example.exam.repository.StudyPlanItemRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyPlanService {
    private final StudyPlanItemRepository planRepository;

    public StudyPlanService(StudyPlanItemRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Transactional(readOnly = true)
    public List<StudyPlanItemResponse> list(Long userId, LocalDate from, LocalDate to) {
        LocalDate[] range = normalizeRange(from, to);
        return listEntities(userId, range[0], range[1]).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public StudyPlanItemResponse create(User user, StudyPlanItemRequest request) {
        StudyPlanItem item = new StudyPlanItem();
        item.setOwner(user);
        applyRequest(item, request, StudyPlanSource.MANUAL);
        planRepository.save(item);
        return toResponse(item);
    }

    @Transactional
    public StudyPlanItemResponse createFromAi(User user, StudyPlanItemRequest request) {
        StudyPlanItem item = new StudyPlanItem();
        item.setOwner(user);
        applyRequest(item, request, StudyPlanSource.AI);
        planRepository.save(item);
        return toResponse(item);
    }

    @Transactional
    public StudyPlanItemResponse update(Long itemId, Long userId, StudyPlanItemRequest request) {
        StudyPlanItem item = requireOwned(itemId, userId);
        applyRequest(item, request, item.getSource());
        return toResponse(item);
    }

    @Transactional
    public StudyPlanItemResponse updateFromAi(Long itemId, Long userId, StudyPlanItemRequest request) {
        StudyPlanItem item = requireOwned(itemId, userId);
        applyRequest(item, request, StudyPlanSource.AI);
        return toResponse(item);
    }

    @Transactional
    public void delete(Long itemId, Long userId) {
        StudyPlanItem item = requireOwned(itemId, userId);
        planRepository.delete(item);
    }

    @Transactional(readOnly = true)
    public StudyPlanItem requireOwned(Long itemId, Long userId) {
        return planRepository.findByIdAndOwnerId(itemId, userId)
                .orElseThrow(() -> new IllegalArgumentException("规划不存在，或你没有访问权限"));
    }

    @Transactional(readOnly = true)
    public List<StudyPlanItem> listEntities(Long userId, LocalDate from, LocalDate to) {
        LocalDate[] range = normalizeRange(from, to);
        return planRepository.findByOwnerIdAndStartDateBetweenOrderByStartDateAscStartTimeAsc(userId, range[0], range[1]);
    }

    public StudyPlanItemResponse toResponse(StudyPlanItem item) {
        return new StudyPlanItemResponse(
                item.getId(),
                item.getTitle(),
                item.getSubject(),
                item.getDescription(),
                item.getItemType(),
                item.getStartDate(),
                item.getStartTime(),
                item.getEndTime(),
                item.getLocation(),
                item.getPriority(),
                item.getStatus(),
                item.getSource(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private void applyRequest(StudyPlanItem item, StudyPlanItemRequest request, StudyPlanSource source) {
        if (request.startDate() == null) {
            throw new IllegalArgumentException("日期不能为空");
        }
        validateTimeRange(request.startTime(), request.endTime());
        item.setTitle(cleanRequired(request.title(), "标题不能为空", 120));
        item.setSubject(cleanOptional(request.subject(), 120));
        item.setDescription(cleanOptional(request.description(), 800));
        item.setItemType(request.itemType() == null ? StudyPlanItemType.SELF_STUDY : request.itemType());
        item.setStartDate(request.startDate());
        item.setStartTime(request.startTime());
        item.setEndTime(request.endTime());
        item.setLocation(cleanOptional(request.location(), 120));
        item.setPriority(request.priority() == null ? StudyPlanPriority.MEDIUM : request.priority());
        item.setStatus(request.status() == null ? StudyPlanStatus.TODO : request.status());
        item.setSource(source);
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("开始时间和结束时间不能为空");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }
    }

    private LocalDate[] normalizeRange(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().minusDays(7) : from;
        LocalDate end = to == null ? start.plusDays(35) : to;
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        if (start.plusDays(120).isBefore(end)) {
            throw new IllegalArgumentException("一次最多查看 120 天规划");
        }
        return new LocalDate[] { start, end };
    }

    private String cleanRequired(String value, String error, int maxLength) {
        String cleaned = cleanOptional(value, maxLength);
        if (cleaned == null || cleaned.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return cleaned;
    }

    private String cleanOptional(String value, int maxLength) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.isEmpty()) return null;
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }
}
