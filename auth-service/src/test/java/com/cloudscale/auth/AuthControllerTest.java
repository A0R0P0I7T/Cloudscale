package com.cloudscale.auth;

import com.cloudscale.auth.api.AuthController;
import com.cloudscale.auth.api.AuthRequests;
import com.cloudscale.auth.api.AuthResponse;
import com.cloudscale.auth.application.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {
    @Test
    void registerReturnsCreatedWithoutAnUnimplementedResourceLocation() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(authService);
        AuthRequests.RegisterRequest request = new AuthRequests.RegisterRequest(
                "CloudScale Demo", "cloudscale-demo", "Ada Lovelace",
                "ada@example.com", "a-long-local-password");
        AuthResponse expected = new AuthResponse(UUID.randomUUID(), UUID.randomUUID(),
                "cloudscale-demo", "CloudScale Demo", "ADMIN", "signed-token", "Bearer", 900);
        when(authService.register(request)).thenReturn(expected);

        var response = controller.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNull();
        assertThat(response.getBody()).isEqualTo(expected);
    }
}
