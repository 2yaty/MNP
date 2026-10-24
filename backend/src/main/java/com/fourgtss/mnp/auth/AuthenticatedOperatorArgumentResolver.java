package com.fourgtss.mnp.auth;

import com.fourgtss.mnp.model.Operator;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves {@link AuthenticatedOperator} parameters from what {@link OrganizationHeaderInterceptor} stored. */
public class AuthenticatedOperatorArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticatedOperator.class)
                && Operator.class.equals(parameter.getParameterType());
    }

    @Override
    public Operator resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                    NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object operator = webRequest.getAttribute(
                OrganizationHeaderInterceptor.AUTHENTICATED_OPERATOR_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (operator == null) {
            throw new IllegalStateException(
                    "No authenticated operator for " + parameter.getExecutable() + "; is the endpoint under /api?");
        }
        return (Operator) operator;
    }
}
