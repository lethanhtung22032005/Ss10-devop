package vn.rikkei.exam.vehiclereservation.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class VehicleReservationToolRegistry {

    private final VehicleReservationTools vehicleReservationTools;

    public VehicleReservationToolRegistry(VehicleReservationTools vehicleReservationTools) {
        this.vehicleReservationTools = vehicleReservationTools;
    }

    public List<ToolCallback> getRecordingCallbacks(ToolExecutionRecorder recorder) {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(vehicleReservationTools)
                .build()
                .getToolCallbacks();
        List<ToolCallback> wrapped = new ArrayList<>();
        for (ToolCallback callback : callbacks) {
            wrapped.add(new RecordingToolCallback(callback, recorder));
        }
        return wrapped;
    }
}
