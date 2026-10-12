package vn.rikkei.exam.vehiclereservation.tool;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ToolExecutionRecorder {

    private final Set<String> executedToolNames = new LinkedHashSet<>();

    public void record(String toolName) {
        if (toolName != null && !toolName.isBlank()) {
            executedToolNames.add(toolName);
        }
    }

    public List<String> getExecutedToolNames() {
        return new ArrayList<>(executedToolNames);
    }

    public boolean isEmpty() {
        return executedToolNames.isEmpty();
    }
}
