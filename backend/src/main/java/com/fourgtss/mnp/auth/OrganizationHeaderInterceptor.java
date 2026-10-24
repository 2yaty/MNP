package com.fourgtss.mnp.auth;

import com.fourgtss.mnp.model.Operator;
import com.fourgtss.mnp.repository.OperatorRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Mocked authentication: identifies the calling operator from the {@code organization} header
 * and rejects the request when the header is missing or unknown.
 */
@Component
public class OrganizationHeaderInterceptor implements HandlerInterceptor {

    public static final String ORGANIZATION_HEADER = "organization";

    static final String AUTHENTICATED_OPERATOR_ATTRIBUTE =
            OrganizationHeaderInterceptor.class.getName() + ".authenticatedOperator";

    private final OperatorRepository operatorRepository;

    public OrganizationHeaderInterceptor(OperatorRepository operatorRepository) {
        this.operatorRepository = operatorRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Only controller methods need an operator; this lets CORS preflight requests through.
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String organization = request.getHeader(ORGANIZATION_HEADER);
        if (organization == null || organization.isBlank()) {
            throw new UnauthenticatedException("Missing '%s' header".formatted(ORGANIZATION_HEADER));
        }

        Operator operator = operatorRepository.findByCode(organization)
                .orElseThrow(() -> new UnauthenticatedException("Unknown organization '%s'".formatted(organization)));
        request.setAttribute(AUTHENTICATED_OPERATOR_ATTRIBUTE, operator);
        return true;
    }
}
