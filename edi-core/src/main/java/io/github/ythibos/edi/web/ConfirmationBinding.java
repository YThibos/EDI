package io.github.ythibos.edi.web;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

final class ConfirmationBinding implements RuntimeHintsRegistrar {

    private static final String EDI_PACKAGES = "io.github.ythibos.edi";

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        new BindingReflectionHintsRegistrar().registerReflectionHints(hints.reflection(), answeredTypes().toArray(Type[]::new));
    }

    private static Set<Class<?>> answeredTypes() {
        Set<Class<?>> reached = new LinkedHashSet<>();
        reach(Confirmation.class, reached);
        return reached;
    }

    private static void reach(Type type, Set<Class<?>> reached) {
        switch (type) {
            case ParameterizedType parameterized -> {
                reach(parameterized.getRawType(), reached);
                for (Type argument : parameterized.getActualTypeArguments()) {
                    reach(argument, reached);
                }
            }
            case Class<?> array when array.isArray() -> reach(array.getComponentType(), reached);
            case Class<?> plain when isEdis(plain) -> reachOnce(plain, reached);
            default -> {
            }
        }
    }

    private static boolean isEdis(Class<?> type) {
        return type.getPackageName().startsWith(EDI_PACKAGES);
    }

    private static void reachOnce(Class<?> type, Set<Class<?>> reached) {
        if (!reached.add(type)) {
            return;
        }
        if (type.isSealed()) {
            for (Class<?> permitted : type.getPermittedSubclasses()) {
                reach(permitted, reached);
            }
        }
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                reach(component.getGenericType(), reached);
            }
        }
    }
}
