package vn.rikkei.exam.vehiclereservation.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public class RecordingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolExecutionRecorder recorder;

    public RecordingToolCallback(ToolCallback delegate, ToolExecutionRecorder recorder) {
        this.delegate = delegate;
        this.recorder = recorder;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        recorder.record(getToolDefinition().name());
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        recorder.record(getToolDefinition().name());
        return delegate.call(toolInput, toolContext);
    }
}
