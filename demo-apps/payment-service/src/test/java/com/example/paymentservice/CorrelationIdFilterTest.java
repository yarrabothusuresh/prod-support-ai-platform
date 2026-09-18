package com.example.paymentservice;

import com.example.paymentservice.filter.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new CorrelationIdFilter();
        MDC.clear();
    }

    @Test
    @DisplayName("Should reuse valid X-Correlation-ID header and populate MDC and response header")
    void testValidCorrelationId() throws ServletException, IOException {
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("REQ-12345-ABC");

        doAnswer(invocation -> {
            assertThat(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY)).isEqualTo("REQ-12345-ABC");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        verify(response).setHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "REQ-12345-ABC");
        // Ensure MDC is cleared after filter execution
        assertThat(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("Should generate new CORR- ID when header is missing")
    void testMissingCorrelationId() throws ServletException, IOException {
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

        doAnswer(invocation -> {
            String corrId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
            assertThat(corrId).isNotNull();
            assertThat(corrId).startsWith("CORR-");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), startsWith("CORR-"));
        assertThat(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("Should generate new CORR- ID when header contains invalid characters")
    void testInvalidCorrelationId() throws ServletException, IOException {
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("BAD ID with spaces; and injection <script>");

        doAnswer(invocation -> {
            String corrId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
            assertThat(corrId).isNotNull();
            assertThat(corrId).startsWith("CORR-");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), startsWith("CORR-"));
        assertThat(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY)).isNull();
    }
}
