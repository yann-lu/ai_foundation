package com.ai.foundation.mediator.agent.executor;

import com.ai.foundation.biz.schema.AgentApiSchemaConfigService;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentApiSchemaConfig;
import com.ai.foundation.dal.entity.AgentToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiToolExecutor {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (compatible; AI-Foundation/1.0; +https://ai-foundation.local)";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final AgentApiSchemaConfigService apiSchemaConfigService;

    public String execute(AgentToolDefinition tool, Map<String, Object> params, String accessToken) {
        if (tool == null || StringUtils.isBlank(tool.getUrl())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "Tool URL 未配置");
        }
        String method = StringUtils.defaultIfBlank(tool.getMethod(), "GET").trim().toUpperCase();
        String baseUrl = resolveBaseUrl(tool.getSchemaCode());
        String fullUrl = buildFullUrl(baseUrl, tool.getUrl());
        boolean isInternal = isInternalUrl(fullUrl);

        Map<String, Object> requestParams = new HashMap<>(sanitizeParams(params));
        // 解析 URL 路径占位符（如 {from}/{to}/{amount}）
        String resolvedUrl = resolvePathParams(fullUrl, requestParams);
        if (!resolvedUrl.equals(fullUrl)) {
            removePathParams(fullUrl, requestParams);
        }
        fullUrl = resolvedUrl;
        Map<String, String> headers = buildHeaders(accessToken, requestParams, isInternal);

        log.info("ApiToolExecutor request, toolId={}, method={}, url={}", tool.getId(), method, fullUrl);
        log.debug("ApiToolExecutor params: {}", requestParams.keySet());

        try {
            WebClient client = webClientBuilder.baseUrl("").build();
            String result;
            if ("POST".equals(method)) {
                result = client.post()
                        .uri(fullUrl)
                        .headers(h -> headers.forEach(h::add))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestParams)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(DEFAULT_TIMEOUT);
            } else {
                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(fullUrl);
                requestParams.forEach((k, v) -> {
                    if (v != null && StringUtils.isNotBlank(String.valueOf(v))) {
                        uriBuilder.queryParam(k, v);
                    }
                });
                result = client.get()
                        .uri(uriBuilder.build().toUri())
                        .headers(h -> headers.forEach(h::add))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(DEFAULT_TIMEOUT);
            }

            if (StringUtils.isBlank(result)) {
                return "未查询到数据";
            }
            log.info("ApiToolExecutor completed, toolId={}, responseLength={}", tool.getId(), result.length());
            return result;
        } catch (WebClientResponseException ex) {
            String respBody = ex.getResponseBodyAsString();
            log.error("ApiToolExecutor HTTP error, toolId={}, url={}, status={}, body={}",
                    tool.getId(), fullUrl, ex.getStatusCode(), respBody);
            String brief = StringUtils.isNotBlank(respBody) && respBody.length() <= 200
                    ? respBody
                    : (respBody != null ? respBody.substring(0, 200) + "..." : "");
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,
                    "API 调用失败(" + ex.getStatusCode() + "): " + brief);
        } catch (Exception ex) {
            log.error("ApiToolExecutor failed, toolId={}, url={}", tool.getId(), fullUrl, ex);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,
                    "API 调用异常: " + (ex.getMessage() != null ? ex.getMessage() : "unknown"));
        }
    }

    private String resolveBaseUrl(String schemaCode) {
        if (StringUtils.isBlank(schemaCode)) {
            return "";
        }
        AgentApiSchemaConfig config = apiSchemaConfigService.getBySchemaCode(schemaCode);
        if (config == null || StringUtils.isBlank(config.getBaseUrl())) {
            return "";
        }
        return config.getBaseUrl().trim();
    }

    private String resolvePathParams(String url, Map<String, Object> params) {
        if (url == null || !url.contains("{")) {
            return url;
        }
        String result = url;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (result.contains(placeholder) && entry.getValue() != null) {
                result = result.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    private void removePathParams(String url, Map<String, Object> params) {
        if (url == null || params == null || params.isEmpty()) {
            return;
        }
        params.keySet().removeIf(key -> url.contains("{" + key + "}"));
    }

    private String buildFullUrl(String baseUrl, String toolUrl) {
        if (StringUtils.isBlank(toolUrl)) {
            return "";
        }
        if (toolUrl.startsWith("http://") || toolUrl.startsWith("https://")) {
            return toolUrl;
        }
        if (StringUtils.isNotBlank(baseUrl)) {
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            String path = toolUrl.startsWith("/") ? toolUrl : "/" + toolUrl;
            return base + path;
        }
        return toolUrl;
    }

    /**
     * 判断是否为内部 API（非 http(s) 绝对 URL 的视为内部）
     */
    private boolean isInternalUrl(String fullUrl) {
        return StringUtils.isNotBlank(fullUrl)
                && !fullUrl.startsWith("http://")
                && !fullUrl.startsWith("https://");
    }

    private Map<String, String> buildHeaders(String accessToken, Map<String, Object> params,
                                              boolean isInternal) {
        Map<String, String> headers = new HashMap<>();
        headers.put(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT);
        headers.put(HttpHeaders.ACCEPT, "application/json, */*;q=0.8");

        if (isInternal) {
            if (StringUtils.isNotBlank(accessToken)) {
                headers.put("x-access-titc-c-token", accessToken);
            }
            Object hotelCode = params.get("hotelCode");
            if (hotelCode != null && StringUtils.isNotBlank(String.valueOf(hotelCode))) {
                headers.put("x-hotel-code", String.valueOf(hotelCode));
            }
        }
        return headers;
    }

    private Map<String, Object> sanitizeParams(Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        if (params == null || params.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object val = entry.getValue();
            if (val == null) {
                continue;
            }
            if (val instanceof String s && StringUtils.isBlank(s)) {
                continue;
            }
            result.put(entry.getKey(), val);
        }
        return result;
    }
}
