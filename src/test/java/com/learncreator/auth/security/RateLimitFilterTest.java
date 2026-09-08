package com.learncreator.auth.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();

    @Test
    void randomAuthPathsShareOneNormalizedBucket() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 31; i++) {
            MockHttpServletRequest request = request("/api/auth/random-" + i);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);

            if (i == 30) {
                assertThat(response.getStatus()).isEqualTo(429);
            }
        }

        verify(chain, times(30)).doFilter(any(), any());
    }

    @Test
    void unrelatedPathsAreNotRateLimited() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 40; i++) {
            filter.doFilter(request("/api/courses/" + i), new MockHttpServletResponse(), chain);
        }
        verify(chain, times(40)).doFilter(any(), any());
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("192.0.2.1");
        return request;
    }
}
