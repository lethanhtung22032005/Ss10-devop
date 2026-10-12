package vn.rikkei.exam.vehiclereservation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import vn.rikkei.exam.vehiclereservation.service.VehicleReservationService.DayAvailability;
import vn.rikkei.exam.vehiclereservation.service.VehicleReservationService.ReservationCreation;
import vn.rikkei.exam.vehiclereservation.service.VehicleReservationService.VehicleAvailability;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VehicleReservationServiceTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 15);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 16);

    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final ResourceTypeRepository resourceTypeRepository = mock(ResourceTypeRepository.class);
    private final ResourceInventoryRepository inventoryRepository = mock(ResourceInventoryRepository.class);
    private final ReservationRequestRepository requestRepository = mock(ReservationRequestRepository.class);

    private final ResourceType std = ResourceType.builder().resourceCode("STD")
            .displayName("Standard xe công tác").maxParticipants(2).active(true).build();
    private final ResourceType prm = ResourceType.builder().resourceCode("PRM")
            .displayName("Premium xe công tác").maxParticipants(4).active(true).build();

    private VehicleReservationService service;

    @BeforeEach
    void setUp() {
        service = new VehicleReservationService(appUserRepository, resourceTypeRepository,
                inventoryRepository, requestRepository);
    }

    @Test
    void availabilityReturnsPerDaySlots() {
        when(resourceTypeRepository.findById("STD")).thenReturn(Optional.of(std));
        when(inventoryRepository.findByResourceTypeResourceCodeAndAvailableDateBetween("STD", D1, D2))
                .thenReturn(List.of(inventory(std, D1, 8), inventory(std, D2, 5)));

        VehicleAvailability result = service.getVehicleAvailability("STD", D1, D2);

        assertThat(result.available()).isTrue();
        assertThat(result.resourceCode()).isEqualTo("STD");
        assertThat(result.days()).extracting(DayAvailability::date).containsExactly(D1, D2);
        assertThat(result.days()).extracting(DayAvailability::availableSlots).containsExactly(8, 5);
    }

    @Test
    void availabilityFlagFalseWhenADayHasNoSlots() {
        when(resourceTypeRepository.findById("PRM")).thenReturn(Optional.of(prm));
        when(inventoryRepository.findByResourceTypeResourceCodeAndAvailableDateBetween("PRM", D1, D2))
                .thenReturn(List.of(inventory(prm, D1, 3)));

        VehicleAvailability result = service.getVehicleAvailability("PRM", D1, D2);

        assertThat(result.available()).isFalse();
        assertThat(result.days()).extracting(DayAvailability::availableSlots).containsExactly(3, 0);
    }

    @Test
    void rejectsRangeWhereStartIsNotBeforeEnd() {
        when(resourceTypeRepository.findById("STD")).thenReturn(Optional.of(std));
        assertThatThrownBy(() -> service.getVehicleAvailability("STD", D1, D1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("startDate phải nhỏ hơn endDate");
    }

    @Test
    void rejectsUnknownResourceType() {
        when(resourceTypeRepository.findById("XXX")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getVehicleAvailability("XXX", D1, D2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không tìm thấy loại xe công tác");
    }

    @Test
    void resolvesPremiumByDisplayKeyword() {
        when(resourceTypeRepository.findById("PRM")).thenReturn(Optional.of(prm));
        when(inventoryRepository.findByResourceTypeResourceCodeAndAvailableDateBetween("PRM", D1, D2))
                .thenReturn(List.of());
        assertThat(service.getVehicleAvailability("Premium", D1, D2).resourceCode()).isEqualTo("PRM");
    }

    @Test
    void createsPendingReservationWithRequestIdAndSummary() {
        AppUser user = user("USR-001");
        when(appUserRepository.findById("USR-001")).thenReturn(Optional.of(user));
        when(resourceTypeRepository.findById("STD")).thenReturn(Optional.of(std));
        when(requestRepository.save(any(ReservationRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationCreation creation = service.createVehicleReservationRequest(
                "USR-001", "STD", D1, D2, 2, "Đi công tác chi nhánh Hà Nội");

        assertThat(creation.requestId()).startsWith("REQ-");
        assertThat(creation.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(creation.summary()).contains(creation.requestId());
        verify(requestRepository).save(any(ReservationRequest.class));
    }

    @Test
    void rejectsWhenUserDoesNotExist() {
        when(appUserRepository.findById("USR-999")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createVehicleReservationRequest(
                "USR-999", "STD", D1, D2, 2, "Mục đích hợp lệ đủ dài mười ký tự"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không tồn tại");
        verify(requestRepository, never()).save(any());
    }

    @Test
    void rejectsMoreThan14Days() {
        when(appUserRepository.findById("USR-001")).thenReturn(Optional.of(user("USR-001")));
        when(resourceTypeRepository.findById("STD")).thenReturn(Optional.of(std));

        LocalDate farEnd = D1.plusDays(14);
        assertThatThrownBy(() -> service.createVehicleReservationRequest(
                "USR-001", "STD", D1, farEnd, 2, "Mục đích hợp lệ đủ dài mười ký tự"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tối đa 14 ngày");
    }

    @Test
    void rejectsOverCapacity() {
        when(appUserRepository.findById("USR-001")).thenReturn(Optional.of(user("USR-001")));
        when(resourceTypeRepository.findById("STD")).thenReturn(Optional.of(std));

        assertThatThrownBy(() -> service.createVehicleReservationRequest(
                "USR-001", "STD", D1, D2, 3, "Mục đích hợp lệ đủ dài mười ký tự"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vượt quá sức chứa tối đa");
    }

    @Test
    void rejectsPremiumBelowTwoPeople() {
        when(appUserRepository.findById("USR-001")).thenReturn(Optional.of(user("USR-001")));
        when(resourceTypeRepository.findById("PRM")).thenReturn(Optional.of(prm));

        assertThatThrownBy(() -> service.createVehicleReservationRequest(
                "USR-001", "PRM", D1, D2, 1, "Mục đích hợp lệ đủ dài mười ký tự"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("từ 2 người trở lên");
    }

    @Test
    void rejectsPurposeShorterThan10Chars() {
        when(appUserRepository.findById("USR-001")).thenReturn(Optional.of(user("USR-001")));
        when(resourceTypeRepository.findById("STD")).thenReturn(Optional.of(std));

        assertThatThrownBy(() -> service.createVehicleReservationRequest(
                "USR-001", "STD", D1, D2, 2, "ngắn"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("10 đến 200 ký tự");
    }

    @Test
    void rejectsPurposeLongerThan200Chars() {
        when(appUserRepository.findById("USR-001")).thenReturn(Optional.of(user("USR-001")));
        when(resourceTypeRepository.findById("STD")).thenReturn(Optional.of(std));

        String longPurpose = "m".repeat(201);
        assertThatThrownBy(() -> service.createVehicleReservationRequest(
                "USR-001", "STD", D1, D2, 2, longPurpose))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("10 đến 200 ký tự");
    }

    @Test
    void rejectsInactiveResourceType() {
        when(appUserRepository.findById("USR-001")).thenReturn(Optional.of(user("USR-001")));
        ResourceType inactive = ResourceType.builder().resourceCode("STD")
                .displayName("Standard xe công tác").maxParticipants(2).active(false).build();
        when(resourceTypeRepository.findById("STD")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.createVehicleReservationRequest(
                "USR-001", "STD", D1, D2, 2, "Mục đích hợp lệ đủ dài mười ký tự"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không còn hoạt động");
    }

    private AppUser user(String id) {
        return AppUser.builder().userId(id).fullName("Nguyen Minh Anh").department("Operations").build();
    }

    private ResourceInventory inventory(ResourceType type, LocalDate date, int slots) {
        return ResourceInventory.builder().resourceType(type).availableDate(date).availableSlots(slots).build();
    }
}
