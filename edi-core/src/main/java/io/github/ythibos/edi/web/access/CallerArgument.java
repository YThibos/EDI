package io.github.ythibos.edi.web.access;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

final class CallerArgument implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Caller.class.equals(parameter.getParameterType());
    }

    @Override
    public Caller resolveArgument(MethodParameter parameter, ModelAndViewContainer container, NativeWebRequest request,
            WebDataBinderFactory binders) {
        Object caller = request.getAttribute(Caller.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (caller instanceof Caller known) {
            return known;
        }
        throw new IllegalStateException("The token filter names no caller for " + parameter.getExecutable().getName() + ".");
    }
}
