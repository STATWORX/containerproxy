package eu.openanalytics.containerproxy.auth.impl.oidc;

import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;


public class AcrTokenValidator implements OAuth2TokenValidator<Jwt>{

    private final ClientRegistration registration;
    private final String acrExpectedValue;

    public AcrTokenValidator(ClientRegistration registration, String acrExpectedValue){
        this.registration = registration;
        this.acrExpectedValue = acrExpectedValue;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        OAuth2TokenValidatorResult allResult;
        OAuth2TokenValidatorResult validationResult = new OidcIdTokenValidator(registration).validate(jwt);
        String acrValue = jwt.getClaimAsString("acr_values");
        if (! acrValue.equals(this.acrExpectedValue)){
            OAuth2Error[] errs = validationResult.getErrors().toArray(new OAuth2Error[validationResult.getErrors().size()+1]);
            errs[validationResult.getErrors().size()] = new OAuth2Error("123s");
            allResult = OAuth2TokenValidatorResult.failure(errs);
        }else{
            allResult = validationResult;
        }
        return allResult;
    }
}
