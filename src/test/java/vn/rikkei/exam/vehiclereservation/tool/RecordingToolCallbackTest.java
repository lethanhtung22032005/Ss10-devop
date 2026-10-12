package vn.rikkei.exam.vehiclereservation.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingToolCallbackTest {

    private static final String TOOL_NAME = "getVehicleAvailability";

    private final ToolExecutionRecorder recorder = new ToolExecutionRecorder();
    private final ToolCallback delegate = new ToolCallback() {
        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder().name(TOOL_NAME).description("tra cứu khả dụng").inputSchema("{}").build();
        }

        @Override
        public String call(String toolInput) {
            return "ok";
        }
    };

    @Test
    void recordsExecutedToolNameAndDelegates() {
        RecordingToolCallback callback = new RecordingToolCallback(delegate, recorder);

        String result = callback.call("{\"resourceType\":\"STD\"}");

        assertThat(result).isEqualTo("ok");
        assertThat(recorder.getExecutedToolNames()).containsExactly(TOOL_NAME);
    }

    @Test
    void recordsOnlyDistinctToolsInOrder() {
        RecordingToolCallback callback = new RecordingToolCallback(delegate, recorder);
        callback.call("1");
        callback.call("2");

        assertThat(recorder.getExecutedToolNames()).containsExactly(TOOL_NAME);
    }
}
