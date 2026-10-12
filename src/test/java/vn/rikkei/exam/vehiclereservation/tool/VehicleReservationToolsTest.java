package vn.rikkei.exam.vehiclereservation.tool;

import org.junit.jupiter.api.Test;
import vn.rikkei.exam.vehiclereservation.exception.BusinessException;
import vn.rikkei.exam.vehiclereservation.service.VehicleReservationService;
import vn.rikkei.exam.vehiclereservation.service.VehicleReservationService.DayAvailability;
import vn.rikkei.exam.vehiclereservation.service.VehicleReservationService.ReservationCreation;
import vn.rikkei.exam.vehiclereservation.service.VehicleReservationService.VehicleAvailability;
import vn.rikkei.exam.vehiclereservation.model.ReservationStatus;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VehicleReservationToolsTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 15);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 16);

    private final VehicleReservationService service = mock(VehicleReservationService.class);
    private final VehicleReservationTools tools = new VehicleReservationTools(service);

    @Test
    void availabilityToolFormatsResult() {
        VehicleAvailability availability = new VehicleAvailability("STD", "Standard xe công tác", 2, true,
                List.of(new DayAvailability(D1, 8), new DayAvailability(D2, 8)));
        when(service.getVehicleAvailability("STD", D1, D2)).thenReturn(availability);

        String result = tools.getVehicleAvailability("STD", "2026-09-15", "2026-09-16");

        assertThat(result).contains("STD").contains("2026-09-15: 8 chỗ trống (khả dụng)").contains("còn khả dụng");
    }

    @Test
    void availabilityToolReturnsErrorForInvalidDate() {
        String result = tools.getVehicleAvailability("STD", "abc", "2026-09-16");
        assertThat(result).startsWith("Lỗi:").contains("yyyy-MM-dd");
    }

    @Test
    void availabilityToolReturnsBusinessErrorAsMessage() {
        when(service.getVehicleAvailability(anyString(), any(), any()))
                .thenThrow(new BusinessException("Khoảng ngày không hợp lệ."));

        String result = tools.getVehicleAvailability("STD", "2026-09-16", "2026-09-15");

        assertThat(result).isEqualTo("Lỗi: Khoảng ngày không hợp lệ.");
    }

    @Test
    void createToolDelegatesAndReturnsSummary() {
        ReservationCreation creation = new ReservationCreation("REQ-123", ReservationStatus.PENDING,
                D1, D2, 2, "Đi công tác", "Đã tạo yêu cầu REQ-123.");
        when(service.createVehicleReservationRequest(eq("USR-001"), eq("STD"), eq(D1), eq(D2), eq(2), anyString()))
                .thenReturn(creation);

        String result = tools.createVehicleReservationRequest("USR-001", "STD", "2026-09-15", "2026-09-16", 2, "Đi công tác Hà Nội");

        verify(service).createVehicleReservationRequest("USR-001", "STD", D1, D2, 2, "Đi công tác Hà Nội");
        assertThat(result).isEqualTo("Đã tạo yêu cầu REQ-123.");
    }

    @Test
    void createToolReturnsErrorWhenParticipantCountMissing() {
        String result = tools.createVehicleReservationRequest("USR-001", "STD", "2026-09-15", "2026-09-16", null, "Đi công tác Hà Nội");
        assertThat(result).isEqualTo("Lỗi: Thiếu số người tham gia.");
    }
}
