package vn.rikkei.exam.vehiclereservation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.rikkei.exam.vehiclereservation.exception.BusinessException;
import vn.rikkei.exam.vehiclereservation.model.AppUser;
import vn.rikkei.exam.vehiclereservation.model.ReservationRequest;
import vn.rikkei.exam.vehiclereservation.model.ReservationStatus;
import vn.rikkei.exam.vehiclereservation.model.ResourceInventory;
import vn.rikkei.exam.vehiclereservation.model.ResourceType;
import vn.rikkei.exam.vehiclereservation.repository.AppUserRepository;
import vn.rikkei.exam.vehiclereservation.repository.ReservationRequestRepository;
import vn.rikkei.exam.vehiclereservation.repository.ResourceInventoryRepository;
import vn.rikkei.exam.vehiclereservation.repository.ResourceTypeRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class VehicleReservationService {

    public static final int MAX_RESERVATION_DAYS = 14;
    public static final int MIN_PURPOSE_LENGTH = 10;
    public static final int MAX_PURPOSE_LENGTH = 200;
    public static final int PREMIUM_MIN_PARTICIPANTS = 2;

    private final AppUserRepository appUserRepository;
    private final ResourceTypeRepository resourceTypeRepository;
    private final ResourceInventoryRepository resourceInventoryRepository;
    private final ReservationRequestRepository reservationRequestRepository;

    public VehicleReservationService(AppUserRepository appUserRepository,
                                     ResourceTypeRepository resourceTypeRepository,
                                     ResourceInventoryRepository resourceInventoryRepository,
                                     ReservationRequestRepository reservationRequestRepository) {
        this.appUserRepository = appUserRepository;
        this.resourceTypeRepository = resourceTypeRepository;
        this.resourceInventoryRepository = resourceInventoryRepository;
        this.reservationRequestRepository = reservationRequestRepository;
    }

    @Transactional(readOnly = true)
    public VehicleAvailability getVehicleAvailability(String resourceType, LocalDate startDate, LocalDate endDate) {
        ResourceType type = resolveResourceType(resourceType);
        validateRange(startDate, endDate);

        List<ResourceInventory> rows = resourceInventoryRepository
                .findByResourceTypeResourceCodeAndAvailableDateBetween(type.getResourceCode(), startDate, endDate);
        List<DayAvailability> days = new ArrayList<>();
        boolean allAvailable = true;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            LocalDate day = date;
            int slots = rows.stream()
                    .filter(r -> r.getAvailableDate().equals(day))
                    .map(ResourceInventory::getAvailableSlots)
                    .mapToInt(Integer::intValue)
                    .sum();
            days.add(new DayAvailability(date, slots));
            if (slots <= 0) {
                allAvailable = false;
            }
        }
        return new VehicleAvailability(type.getResourceCode(), type.getDisplayName(),
                type.getMaxParticipants(), allAvailable, days);
    }

    @Transactional
    public ReservationCreation createVehicleReservationRequest(String userId, String resourceType,
                                                               LocalDate startDate, LocalDate endDate,
                                                               int participantCount, String purpose) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Người dùng '" + userId + "' không tồn tại."));
        ResourceType type = resolveResourceType(resourceType);
        validateRange(startDate, endDate);

        long requestedDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (requestedDays > MAX_RESERVATION_DAYS) {
            throw new BusinessException("Yêu cầu tối đa " + MAX_RESERVATION_DAYS + " ngày.");
        }
        if (participantCount <= 0) {
            throw new BusinessException("Số người tham gia phải lớn hơn 0.");
        }
        if (participantCount > type.getMaxParticipants()) {
            throw new BusinessException("Số người tham gia (" + participantCount
                    + ") vượt quá sức chứa tối đa (" + type.getMaxParticipants()
                    + ") của nhóm " + type.getResourceCode() + ".");
        }
        if ("PRM".equals(type.getResourceCode()) && participantCount < PREMIUM_MIN_PARTICIPANTS) {
            throw new BusinessException("Nhóm PREMIUM chỉ dành cho yêu cầu có từ "
                    + PREMIUM_MIN_PARTICIPANTS + " người trở lên.");
        }
        if (purpose == null || purpose.strip().length() < MIN_PURPOSE_LENGTH
                || purpose.strip().length() > MAX_PURPOSE_LENGTH) {
            throw new BusinessException("Mục đích phải mô tả rõ từ " + MIN_PURPOSE_LENGTH
                    + " đến " + MAX_PURPOSE_LENGTH + " ký tự.");
        }

        String requestId = "REQ-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Instant now = Instant.now();
        ReservationRequest request = ReservationRequest.builder()
                .requestId(requestId)
                .requester(user)
                .resourceType(type)
                .startDate(startDate)
                .endDate(endDate)
                .participantCount(participantCount)
                .purpose(purpose.strip())
                .status(ReservationStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        reservationRequestRepository.save(request);

        String summary = "Đã tạo yêu cầu đặt xe " + type.getResourceCode() + " - " + type.getDisplayName()
                + " từ " + startDate + " đến " + endDate + " cho " + participantCount + " người"
                + " (mục đích: " + purpose.strip() + "). Trạng thái: PENDING. Mã yêu cầu: " + requestId + ".";
        return new ReservationCreation(requestId, ReservationStatus.PENDING, startDate, endDate,
                participantCount, purpose.strip(), summary);
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException("Thiếu ngày bắt đầu hoặc ngày kết thúc.");
        }
        if (!startDate.isBefore(endDate)) {
            throw new BusinessException("startDate phải nhỏ hơn endDate (startDate < endDate).");
        }
    }

    private ResourceType resolveResourceType(String resourceType) {
        String raw = resourceType == null ? "" : resourceType.strip();
        ResourceType type = null;
        if (!raw.isEmpty()) {
            type = resourceTypeRepository.findById(raw.toUpperCase(Locale.ROOT)).orElse(null);
        }
        if (type == null && !raw.isEmpty()) {
            String lower = raw.toLowerCase(Locale.ROOT);
            String key = null;
            if (lower.contains("premium") || lower.contains("prm")) {
                key = "PRM";
            } else if (lower.contains("standard") || lower.contains("std")) {
                key = "STD";
            }
            if (key != null) {
                type = resourceTypeRepository.findById(key).orElse(null);
            }
        }
        if (type == null) {
            throw new BusinessException("Không tìm thấy loại xe công tác '" + resourceType
                    + "'. Hỗ trợ: STD (Standard) hoặc PRM (Premium).");
        }
        if (!Boolean.TRUE.equals(type.getActive())) {
            throw new BusinessException("Loại xe công tác '" + type.getResourceCode() + "' không còn hoạt động.");
        }
        return type;
    }

    public record DayAvailability(LocalDate date, int availableSlots) {
        public boolean available() {
            return availableSlots > 0;
        }
    }

    public record VehicleAvailability(String resourceCode, String resourceName, Integer maxParticipants,
                                      boolean available, List<DayAvailability> days) {
    }

    public record ReservationCreation(String requestId, ReservationStatus status, LocalDate startDate,
                                      LocalDate endDate, Integer participantCount, String purpose, String summary) {
    }
}
