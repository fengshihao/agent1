package com.agent1.javaagent.tool.anno;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolArgumentValidator;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/** 把带 {@link Tool} 的实例收成运行面 {@link AgentTool}。 */
public final class AnnotatedTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AnnotatedTools() {
    }

    public static List<AgentTool> from(Object instance) {
        if (instance == null) {
            throw new IllegalArgumentException("tool instance required");
        }
        List<AgentTool> tools = new ArrayList<>();
        for (Method method : instance.getClass().getDeclaredMethods()) {
            Tool annotation = method.getAnnotation(Tool.class);
            if (annotation == null) {
                continue;
            }
            method.setAccessible(true);
            tools.add(new AnnotatedAgentTool(instance, method, annotation));
        }
        if (tools.isEmpty()) {
            throw new IllegalArgumentException(
                "no @Tool methods on " + instance.getClass().getName()
            );
        }
        return List.copyOf(tools);
    }

    private static final class AnnotatedAgentTool implements AgentTool {
        private final Object instance;
        private final Method method;
        private final Tool annotation;
        private final JsonNode schema;
        private final String name;

        private AnnotatedAgentTool(Object instance, Method method, Tool annotation) {
            this.instance = instance;
            this.method = method;
            this.annotation = annotation;
            String declared = annotation.name();
            this.name = declared == null || declared.isBlank() ? method.getName() : declared;
            this.schema = resolveSchema(instance, this.name, method);
            rejectUnsupportedReturn(method);
            rejectUnannotatedParameters(method);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return annotation.description();
        }

        @Override
        public JsonNode parametersSchema() {
            return schema;
        }

        @Override
        public long suggestedTimeoutMs(JsonNode parameters, long fallbackMs) {
            long requested = annotation.timeoutMs();
            if (requested <= 0) {
                return fallbackMs;
            }
            return Math.max(fallbackMs, requested);
        }

        @Override
        public ToolExecutionResult execute(
            String toolCallId,
            JsonNode parameters,
            CancellationToken cancellationToken,
            ToolUpdateListener onUpdate
        ) throws Exception {
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return ToolExecutionResult.text("错误：执行已取消");
            }
            var validationError = ToolArgumentValidator.validateRequired(schema, parameters);
            if (validationError.isPresent()) {
                throw new IllegalArgumentException(validationError.get());
            }
            Object value = invoke(parameters, cancellationToken);
            if (value instanceof ToolExecutionResult result) {
                return result;
            }
            return ToolExecutionResult.text(value == null ? "" : String.valueOf(value));
        }

        private Object invoke(JsonNode parameters, CancellationToken cancellationToken) throws Exception {
            Parameter[] params = method.getParameters();
            Object[] args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                Parameter parameter = params[i];
                if (parameter.getType() == CancellationToken.class) {
                    args[i] = cancellationToken;
                    continue;
                }
                ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
                if (toolParam == null) {
                    throw new IllegalArgumentException(
                        "参数缺少 @ToolParam: " + method.getName()
                    );
                }
                JsonNode raw = parameters == null ? null : parameters.get(toolParam.name());
                args[i] = convert(toolParam.name(), raw, parameter.getType());
            }
            try {
                return method.invoke(instance, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw e;
            }
        }
    }

    private static void rejectUnannotatedParameters(Method method) {
        for (Parameter parameter : method.getParameters()) {
            if (parameter.getType() == CancellationToken.class) {
                continue;
            }
            if (parameter.getAnnotation(ToolParam.class) == null) {
                throw new IllegalArgumentException(
                    "参数缺少 @ToolParam: " + method.getName()
                );
            }
        }
    }

    private static void rejectUnsupportedReturn(Method method) {
        Class<?> type = method.getReturnType();
        if (type == void.class || type == String.class || type == ToolExecutionResult.class) {
            return;
        }
        throw new IllegalArgumentException(
            "@Tool 只支持 String 或 ToolExecutionResult 返回值: " + method.getName()
        );
    }

    private static JsonNode resolveSchema(Object instance, String toolName, Method method) {
        if (instance instanceof ToolSchemaSource source) {
            JsonNode override = source.parametersSchema(toolName);
            if (override != null) {
                return override;
            }
        }
        return generateSchema(method);
    }

    static JsonNode generateSchema(Method method) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        var required = MAPPER.createArrayNode();
        for (Parameter parameter : method.getParameters()) {
            if (parameter.getType() == CancellationToken.class) {
                continue;
            }
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            if (toolParam == null) {
                continue;
            }
            ObjectNode property = MAPPER.createObjectNode();
            property.put("type", jsonType(parameter.getType()));
            if (!toolParam.description().isEmpty()) {
                property.put("description", toolParam.description());
            }
            properties.set(toolParam.name(), property);
            if (toolParam.required()) {
                required.add(toolParam.name());
            }
        }
        schema.set("properties", properties);
        if (!required.isEmpty()) {
            schema.set("required", required);
        }
        return schema;
    }

    private static String jsonType(Class<?> type) {
        if (type == String.class) {
            return "string";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            return "number";
        }
        return "object";
    }

    private static Object convert(String name, JsonNode raw, Class<?> type) {
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            if (type.isPrimitive()) {
                throw new IllegalArgumentException("错误：参数 " + name + " 不能为空");
            }
            return null;
        }
        if (type == String.class) {
            if (!raw.isTextual()) {
                throw new IllegalArgumentException("错误：参数 " + name + " 应为字符串");
            }
            return raw.asText();
        }
        if (type == boolean.class || type == Boolean.class) {
            if (!raw.isBoolean()) {
                throw new IllegalArgumentException("错误：参数 " + name + " 应为布尔值");
            }
            return raw.booleanValue();
        }
        if (type == int.class || type == Integer.class) {
            if (!raw.isIntegralNumber()) {
                throw new IllegalArgumentException("错误：参数 " + name + " 应为整数");
            }
            return raw.intValue();
        }
        if (type == long.class || type == Long.class) {
            if (!raw.isIntegralNumber()) {
                throw new IllegalArgumentException("错误：参数 " + name + " 应为整数");
            }
            return raw.longValue();
        }
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            if (!raw.isNumber()) {
                throw new IllegalArgumentException("错误：参数 " + name + " 应为数字");
            }
            return type == float.class || type == Float.class ? (float) raw.doubleValue() : raw.doubleValue();
        }
        throw new IllegalArgumentException("错误：参数 " + name + " 的类型不受支持");
    }
}
