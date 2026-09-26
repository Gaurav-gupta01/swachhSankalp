package CleanVision.security;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import CleanVision.domain.Role;
import CleanVision.domain.User;
import CleanVision.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_USER_ID = "cvUserId";
    public static final String CURRENT_USER = "cvCurrentUser";

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String uri = request.getRequestURI();
        if (isPublic(uri, request.getMethod())) {
            resolve(request);
            return true;
        }

        User user = resolve(request);
        if (user == null) {
            if (uri.startsWith("/api/")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"Login required\"}");
            } else {
                response.sendRedirect("/login?next=" + java.net.URLEncoder.encode(uri, java.nio.charset.StandardCharsets.UTF_8));
            }
            return false;
        }

        if (uri.startsWith("/team")) {
            if (user.getRole() != Role.TEAM_LEADER) {
                return forbidden(response, uri, "Team leader access only");
            }
        } else if (uri.startsWith("/admin/users") || uri.startsWith("/api/users")) {
            if (user.getRole() != Role.SUPER_ADMIN) {
                return forbidden(response, uri, "Super admin access only");
            }
        } else if (uri.startsWith("/admin") || uri.startsWith("/api/dashboard")) {
            if (!user.getRole().isOfficer()) {
                return forbidden(response, uri, "Municipal officer access only");
            }
        }

        request.setAttribute(CURRENT_USER, user);
        return true;
    }

    private boolean forbidden(HttpServletResponse response, String uri, String message) throws IOException {
        if (uri.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, message);
        }
        return false;
    }

    public User resolve(HttpServletRequest request) {
        Object cached = request.getAttribute(CURRENT_USER);
        if (cached instanceof User user) {
            return user;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object id = session.getAttribute(SESSION_USER_ID);
        if (!(id instanceof Long userId)) {
            return null;
        }
        try {
            User user = authService.findById(userId);
            request.setAttribute(CURRENT_USER, user);
            return user;
        } catch (RuntimeException ex) {
            session.invalidate();
            return null;
        }
    }

    private boolean isPublic(String uri, String method) {
        if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/images/")
                || uri.startsWith("/uploads/") || uri.startsWith("/webjars/") || uri.startsWith("/vendor/")) {
            return true;
        }
        if (uri.equals("/") || uri.equals("/error") || uri.equals("/favicon.ico")) {
            return true;
        }
        if (uri.startsWith("/api/auth/")) {
            return true;
        }
        return uri.equals("/login") || uri.equals("/register")
                || (uri.equals("/logout") && "GET".equalsIgnoreCase(method));
    }
}
