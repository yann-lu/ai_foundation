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
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CLI API 调用执行器。
 *
 * <p>原实现基于 Spring WebClient（Netty 客户端），在 macOS 环境下因
 * io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider 缺失
 * native 实现，所有出站 HTTPS 都会抛 UnknownHostException。改为 JDK 11+
 * 内置 {@link java.net.http.HttpClient}，走 JDK 系统 DNS 解析，绕开 Netty。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiToolExecutor {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (compatible; AI-Foundation/1.0; +https://ai-foundation.local)";

    private final ObjectMapper objectMapper;
    private final AgentApiSchemaConfigService apiSchemaConfigService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(DEFAULT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public String execute(AgentToolDefinition tool, Map<String, Object> params, String accessToken) {
        if (tool == null || StringUtils.isBlank(tool.getUrl())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "Tool URL 未配置");
        }
        String method = StringUtils.defaultIfBlank(tool.getMethod(), "GET").trim().toUpperCase();
        String baseUrl = resolveBaseUrl(tool.getSchemaCode());
        String fullUrl = buildFullUrl(baseUrl, tool.getUrl());
        boolean isInternal = isInternalUrl(fullUrl);

        Map<String, Object> requestParams = new LinkedHashMap<>(sanitizeParams(params));
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
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .timeout(DEFAULT_TIMEOUT)
                    .header(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT)
                    .header(HttpHeaders.ACCEPT, "application/json, */*;q=0.8");
            headers.forEach(builder::header);

            HttpRequest request;
            if ("POST".equals(method)) {
                String body = objectMapper.writeValueAsString(requestParams);
                request = builder
                        .uri(URI.create(fullUrl))
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
            } else {
                String urlWithQuery = appendQueryParams(fullUrl, requestParams);
                request = builder
                        .uri(URI.create(urlWithQuery))
                        .GET()
                        .build();
            }

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String body = response.body();
            if (status >= 200 && status < 300) {
                if (StringUtils.isBlank(body)) {
                    return "未查询到数据";
                }
                log.info("ApiToolExecutor completed, toolId={}, responseLength={}", tool.getId(), body.length());
                return body;
            }
            // 4xx / 5xx 抛业务异常
            String brief = StringUtils.isNotBlank(body) && body.length() <= 200
                    ? body
                    : (body != null ? body.substring(0, Math.min(200, body.length())) + "..." : "");
            log.error("ApiToolExecutor HTTP error, toolId={}, url={}, status={}, body={}",
                    tool.getId(), fullUrl, status, brief);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,
                    "API 调用失败(" + status + "): " + brief);
        } catch (BusinessException ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            log.error("ApiToolExecutor timeout, toolId={}, url={}", tool.getId(), fullUrl);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,
                    "API 调用超时: " + (ex.getMessage() != null ? ex.getMessage() : "unknown"));
        } catch (java.net.UnknownHostException ex) {
            log.error("ApiToolExecutor DNS failed, toolId={}, url={}", tool.getId(), fullUrl, ex);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,
                    "API 调用异常: " + (ex.getMessage() != null ? ex.getMessage() : "unknown"));
        } catch (Exception ex) {
            log.error("ApiToolExecutor failed, toolId={}, url={}", tool.getId(), fullUrl, ex);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,
                    "API 调用异常: " + (ex.getMessage() != null ? ex.getMessage() : "unknown"));
        }
    }

    private String appendQueryParams(String url, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        boolean hasQuery = url.indexOf('?') >= 0;
        char sep = hasQuery ? '&' : '?';
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object v = entry.getValue();
            if (v == null) {
                continue;
            }
            String s = String.valueOf(v);
            if (StringUtils.isBlank(s)) {
                continue;
            }
            sb.append(sep).append(encode(entry.getKey())).append('=').append(encode(s));
            sep = '&';
        }
        return sb.toString();
    }

    private static String encode(String raw) {
        return java.net.URLEncoder.encode(raw, StandardCharsets.UTF_8);
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

    private boolean isInternalUrl(String fullUrl) {
        return StringUtils.isNotBlank(fullUrl)
                && !fullUrl.startsWith("http://")
                && !fullUrl.startsWith("https://");
    }

    private Map<String, String> buildHeaders(String accessToken, Map<String, Object> params,
                                              boolean isInternal) {
        Map<String, String> headers = new HashMap<>();
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
        Map<String, Object> result = new LinkedHashMap<>();
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
