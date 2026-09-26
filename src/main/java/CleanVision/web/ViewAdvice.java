package CleanVision.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import CleanVision.domain.User;
import CleanVision.security.AuthInterceptor;
import CleanVision.service.AppException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@ControllerAdvice
@RequiredArgsConstructor
public class ViewAdvice {

    private final AuthInterceptor authInterceptor;

    @ExceptionHandler(AppException.class)
    public Object appException(AppException ex, HttpServletRequest request) {
        HttpStatus status = ex.getMessage() != null && ex.getMessage().toLowerCase().contains("not found")
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        if (isApi(request)) {
            return json(status, ex.getMessage());
        }
        ModelAndView view = new ModelAndView("error");
        view.setStatus(status);
        view.addObject("status", status.value());
        view.addObject("message", ex.getMessage());
        view.addObject("currentUser", currentUser(request));
        return view;
    }

    @ExceptionHandler(Exception.class)
    public Object generic(Exception ex, HttpServletRequest request) {
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.valueOf(rse.getStatusCode().value());
            String message = rse.getReason() == null ? status.getReasonPhrase() : rse.getReason();
            if (isApi(request)) {
                return json(status, message);
            }
            ModelAndView secured = new ModelAndView("error");
            secured.setStatus(status);
            secured.addObject("status", status.value());
            secured.addObject("message", message);
            secured.addObject("currentUser", currentUser(request));
            return secured;
        }

        if (isApi(request)) {
            return json(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
        }
        ModelAndView view = new ModelAndView("error");
        view.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        view.addObject("status", 500);
        view.addObject("message", "Something went wrong while handling this request.");
        view.addObject("detail", ex.getMessage());
        view.addObject("currentUser", currentUser(request));
        return view;
    }

    @ModelAttribute("currentUser")
    public User currentUser(HttpServletRequest request) {
        Object cached = request.getAttribute(AuthInterceptor.CURRENT_USER);
        if (cached instanceof User user) {
            return user;
        }
        return authInterceptor.resolve(request);
    }

    @ModelAttribute("appTitle")
    public String appTitle() {
        return "CleanVision AI";
    }

    private boolean isApi(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }

    private ResponseEntity<Map<String, Object>> json(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(Map.of("success", false, "message", message == null ? status.getReasonPhrase() : message));
    }
}
