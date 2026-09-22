package org.example.reportingservice.system.audit.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
public class DataMaskingUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> SENSITIVE_KEYS = new HashSet<>(Arrays.asList(
            "password",
            "currentpassword",
            "newpassword",
            "confirmpassword",
            "token",
            "accesstoken",
            "refreshtoken",
            "secret",
            "secretkey",
            "authorization",
            "creditcard",
            "cvv"
    ));

    private static final String MASK_VALUE = "******";

    public static String maskJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }

        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(jsonString);
            maskNode(rootNode);
            return OBJECT_MAPPER.writeValueAsString(rootNode);
        } catch (Exception e) {
            // Fallback regex nếu chuỗi không phải valid JSON thuần túy
            return maskWithRegex(jsonString);
        }
    }

    public static String maskObject(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(obj);
            return maskJson(json);
        } catch (Exception e) {
            log.debug("Lỗi khi chuyển đổi object sang JSON để mask: {}", e.getMessage());
            return null;
        }
    }

    private static void maskNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<String> fieldNames = objectNode.fieldNames();
            while (fieldNames.hasNext()) {
                String key = fieldNames.next();
                JsonNode value = objectNode.get(key);

                if (isSensitiveKey(key)) {
                    objectNode.put(key, MASK_VALUE);
                } else if (value.isContainerNode()) {
                    maskNode(value);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                maskNode(child);
            }
        }
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String normalized = key.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return SENSITIVE_KEYS.contains(normalized);
    }

    private static String maskWithRegex(String text) {
        if (text == null) return null;
        String result = text;
        for (String key : SENSITIVE_KEYS) {
            Pattern pattern = Pattern.compile("(\"" + key + "\"\\s*:\\s*\")[^\"]*(\")", Pattern.CASE_INSENSITIVE);
            result = pattern.matcher(result).replaceAll("$1" + MASK_VALUE + "$2");
        }
        return result;
    }
}
