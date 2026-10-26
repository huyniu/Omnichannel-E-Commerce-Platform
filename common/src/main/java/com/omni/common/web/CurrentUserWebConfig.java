package com.omni.common.web;

import com.omni.common.exception.BusinessException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class CurrentUserWebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserResolver());
    }

    static class CurrentUserResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthUser.class)
                    && parameter.getParameterType().equals(CurrentUser.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest request, WebDataBinderFactory binderFactory) {
            String id = request.getHeader(CurrentUser.HEADER_USER_ID);
            if (!StringUtils.hasText(id)) {
                throw BusinessException.unauthorized("Authentication required");
            }
            String rolesHeader = request.getHeader(CurrentUser.HEADER_USER_ROLES);
            Set<String> roles = rolesHeader == null ? Set.of() : Arrays.stream(rolesHeader.split(","))
                    .map(String::trim).filter(StringUtils::hasText).collect(Collectors.toUnmodifiableSet());
            return new CurrentUser(Long.valueOf(id), request.getHeader(CurrentUser.HEADER_USER_EMAIL), roles);
        }
    }
}
