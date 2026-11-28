package com.techbookstore.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.net.URI;
import java.util.Arrays;

/**
 * Controller for handling language switching.
 */
@Controller
public class LanguageController {
    
    @GetMapping("/language/switch")
    public String switchLanguage(@RequestParam String lang, 
                                HttpServletRequest request,
                                HttpServletResponse response) {
        LocaleResolver localeResolver = RequestContextUtils.getLocaleResolver(request);
        if (localeResolver != null) {
            Locale locale = "en".equals(lang) ? Locale.ENGLISH : Locale.JAPANESE;
            localeResolver.setLocale(request, response, locale);
        }
        
        // Redirect to referer or home
        String referer = request.getHeader("Referer");
        return "redirect:" + resolveSafeRedirect(referer, request);
    }

    private String resolveSafeRedirect(
            String referer, HttpServletRequest request) {

        if (referer == null || referer.isBlank() || referer.startsWith("//")) {
            return "/";
        }

        try {
            URI uri = URI.create(referer);

            if (uri.isAbsolute()) {
                String scheme = uri.getScheme();

                if (!"http".equalsIgnoreCase(scheme)
                        && !"https".equalsIgnoreCase(scheme)) {
                    return "/";
                }

                int port = uri.getPort() == -1
                        ? ("https".equalsIgnoreCase(scheme) ? 443 : 80)
                        : uri.getPort();

                if (uri.getHost() == null
                        || uri.getUserInfo() != null
                        || !uri.getHost().equalsIgnoreCase(request.getServerName())
                        || !scheme.equalsIgnoreCase(request.getScheme())
                        || port != request.getServerPort()) {
                    return "/";
                }
            } else if (uri.getRawAuthority() != null) {
                return "/";
            }

            String path = uri.getRawPath();

            if (path == null
                    || !path.matches("/[A-Za-z0-9._~/-]*")
                    || path.contains("//")
                    || Arrays.stream(path.split("/"))
                    .anyMatch(segment ->
                            ".".equals(segment) || "..".equals(segment))) {
                return "/";
            }

            return uri.getRawQuery() == null
                    ? path
                    : path + "?" + uri.getRawQuery();

        } catch (IllegalArgumentException exception) {
            return "/";
        }
    }
}