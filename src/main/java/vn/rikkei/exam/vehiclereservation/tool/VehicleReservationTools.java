package vn.rikkei.exam.vehiclereservation.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import vn.rikkei.exam.vehiclereservation.exception.BusinessException;
import vn.rikkei.exam.vehiclereservation.service.VehicleReservationService;
import vn.rikkei.exam.vehiclereservation.service.VehicleReservationService.DayAvailability;
import vn.rikkei.exam.vehiclereservation.service.VehicleReservationService.ReservationCreation;
import vn.rikkei.exam.vehiclereservation.service.VehicleReservationService.VehicleAvailability;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
public class VehicleReservationTools {

    private static final String DATE_INVALID = "Lỗi: Ngày '%s' không đúng định dạng yyyy-MM-dd.";

    private final VehicleReservationService vehicleReservationService;

    public VehicleReservationTools(VehicleReservationService vehicleReservationService) {
        this.vehicleReservationService = vehicleReservationService;
    }

    @Tool(name = "getVehicleAvailability", description = "Tra cứu tình trạng còn khả dụng của xe công tác theo loại xe và khoảng ngày (startDate < endDate). resourceType nhận mã STD hoặc PRM, hoặc tên Standard/Premium.")
    public String getVehicleAvailability(
            @ToolParam(description = "Loại xe công tác: STD (Standard, tối đa 2 người) hoặc PRM (Premium, tối đa 4 người)") String resourceType,
            @ToolParam(description = "Ngày bắt đầu định dạng yyyy-MM-dd") String startDate,
            @ToolParam(description = "Ngày kết thúc định dạng yyyy-MM-dd, phải sau startDate") String endDate) {
        try {
            LocalDate start = parseDate(startDate);
            LocalDate end = parseDate(endDate);
            VehicleAvailability result = vehicleReservationService.getVehicleAvailability(resourceType, start, end);
            return formatAvailability(result);
        } catch (BusinessException e) {
            return "Lỗi: " + e.getMessage();
        }
    }

    @Tool(name = "createVehicleReservationRequest", description = "Tạo yêu cầu đặt xe công tác (trạng thái PENDING). Kiểm tra người dùng, khoảng ngày tối đa 14 ngày, sức chứa, PREMIUM tối thiểu 2 người, mục đích 10-200 ký tự. Trả về requestId và summary.")
    public String createVehicleReservationRequest(
            @ToolParam(description = "Mã người dùng yêu cầu, ví dụ USR-001") String userId,
            @ToolParam(description = "Loại xe công tác: STD (Standard, tối đa 2 người) hoặc PRM (Premium, tối đa 4 người)") String resourceType,
            @ToolParam(description = "Ngày bắt đầu định dạng yyyy-MM-dd") String startDate,
            @ToolParam(description = "Ngày kết thúc định dạng yyyy-MM-dd, phải sau startDate") String endDate,
            @ToolParam(description = "Số người tham gia") Integer participantCount,
            @ToolParam(description = "Mục đích sử dụng xe, từ 10 đến 200 ký tự") String purpose) {
        try {
            LocalDate start = parseDate(startDate);
            LocalDate end = parseDate(endDate);
            if (participantCount == null) {
                throw new BusinessException("Thiếu số người tham gia.");
            }
            ReservationCreation creation = vehicleReservationService.createVehicleReservationRequest(
                    userId, resourceType, start, end, participantCount, purpose);
            return creation.summary();
        } catch (BusinessException e) {
            return "Lỗi: " + e.getMessage();
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("Thiếu ngày (định dạng yyyy-MM-dd).");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(String.format(DATE_INVALID, value));
        }
    }

    private String formatAvailability(VehicleAvailability result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Kết quả tra cứu xe ").append(result.resourceCode()).append(" - ")
                .append(result.resourceName()).append(" (sức chứa tối đa ")
                .append(result.maxParticipants()).append(" người):\n");
        for (DayAvailability day : result.days()) {
            sb.append("- ").append(day.date())
                    .append(": ").append(day.availableSlots()).append(" chỗ trống")
                    .append(day.available() ? " (khả dụng)" : " (hết chỗ)").append('\n');
        }
        if (result.available()) {
            sb.append("Kết luận: xe còn khả dụng trong toàn bộ khoảng ngày yêu cầu.");
        } else {
            sb.append("Kết luận: không còn đủ khả dụng cho toàn bộ khoảng ngày yêu cầu.");
        }
        return sb.toString();
    }
}
