package eu.openanalytics.containerproxy.auth.impl.oidc;


import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;

import javax.inject.Inject;

@Configuration
@ConditionalOnProperty(name="proxy.authentication", havingValue = "openid")
public class OIDCConfiguration {

    @Inject
    Environment environment;

    @Bean
    public JwtDecoderFactory<ClientRegistration> customJwtDecoderFactory(){
        String acrValue = environment.getProperty("openid.acr-value");
        OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
        factory.setJwtValidatorFactory(clientRegistration -> new AcrTokenValidator(clientRegistration, "daimler:idp:gas:strong"));
        return factory;
    }


}
