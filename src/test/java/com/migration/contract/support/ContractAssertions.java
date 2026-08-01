package com.migration.contract.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

/**
 * Shared JSON comparison helpers for contract tests.
 */
public final class ContractAssertions {

    private ContractAssertions() {
    }

    public static ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    public static void assertJsonEqual(String expected, String actual) throws JSONException {
        JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
    }

    public static void assertJsonEqualLenient(String expected, String actual) throws JSONException {
        JSONAssert.assertEquals(expected, actual, JSONCompareMode.LENIENT);
    }

    public static void assertCreateOrderResponse(String expectedTemplate, String actual) throws JSONException {
        String normalizedExpected = stripVolatileCreateOrderFields(expectedTemplate);
        String normalizedActual = stripVolatileCreateOrderFields(actual);
        JSONAssert.assertEquals(normalizedExpected, normalizedActual, JSONCompareMode.STRICT);
    }

    private static String stripVolatileCreateOrderFields(String json) {
        try {
            var mapper = objectMapper();
            var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(json);
            root.remove("order_id");
            root.remove("order_date");
            if (root.has("line_items") && root.get("line_items").isArray()) {
                for (var item : root.withArray("line_items")) {
                    if (item instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
                        objectNode.remove("line_item_id");
                    }
                }
            }
            return mapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to normalize create-order JSON", ex);
        }
    }
}
