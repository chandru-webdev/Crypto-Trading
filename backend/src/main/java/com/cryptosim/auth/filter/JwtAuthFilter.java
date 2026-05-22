package com.cryptosim.auth.filter;

import com.cryptosim.auth.service.CustomUserDetailsService;
import com.cryptosim.auth.service.JwtService;
import com.cryptosim.common.exception.InvalidTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request. If a valid access JWT is present, it loads the user
 * and puts an authenticated principal into the SecurityContext.
 *
 * Unauthenticated requests are passed through unchanged — the security config
 * decides whether the target endpoint requires authentication.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwt;
    private final CustomUserDetailsService userDetails;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(req, res);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            jwt.validate(token);
            if (!jwt.isAccessToken(token)) {
                // refresh tokens must never authenticate API calls
                chain.doFilter(req, res);
                return;
            }
            Long userId = jwt.extractUserId(token);
            // Only load user when context is empty to avoid clobbering a higher-priority auth.
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails ud = userDetails.loadById(userId);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (InvalidTokenException ex) {
            // Don't fail the chain; let downstream security rules decide
            // (anonymous request -> 401 on protected endpoint, 200 on public).
        }

        chain.doFilter(req, res);
    }
}
