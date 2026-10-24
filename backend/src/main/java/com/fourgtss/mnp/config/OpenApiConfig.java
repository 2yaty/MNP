package com.fourgtss.mnp.config;

import java.util.List;

import com.fourgtss.mnp.auth.OrganizationHeaderInterceptor;
import com.fourgtss.mnp.model.Operator;
import com.fourgtss.mnp.repository.OperatorRepository;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at {@code /swagger-ui.html}. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("MNP API")
                .version("v1")
                .description("Mobile Number Portability: submit, accept and reject number porting requests."));
    }

    /** Adds the mocked-auth header to every operation, as a dropdown of the known operators. */
    @Bean
    public OperationCustomizer organizationHeader(OperatorRepository operatorRepository) {
        return (operation, handlerMethod) -> {
            List<String> codes = operatorRepository.findAll().stream().map(Operator::getCode).sorted().toList();
            return operation.addParametersItem(new HeaderParameter()
                    .name(OrganizationHeaderInterceptor.ORGANIZATION_HEADER)
                    .required(true)
                    .description("The calling operator (mocked authentication)")
                    .schema(new StringSchema()._enum(codes)));
        };
    }
}
