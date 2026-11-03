package io.github.ythibos.edi.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.ReflectionHintsPredicates;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import io.github.ythibos.edi.domain.tracker.TicketSettlement;

class ConfirmationBindingTest {

    private static final ReflectionHintsPredicates REFLECTION = RuntimeHintsPredicates.reflection();

    private final RuntimeHints hints = registered();

    @Test
    void bindsEveryConfirmationTheCoreCanAnswer() {
        assertThat(Confirmation.class.getPermittedSubclasses()).isNotEmpty().allSatisfy(this::assertBound);
    }

    @Test
    void bindsEveryCaseOfASealedPartOfAConfirmation() {
        assertThat(TicketSettlement.class.getPermittedSubclasses()).isNotEmpty().allSatisfy(this::assertBound);
    }

    private static RuntimeHints registered() {
        RuntimeHints hints = new RuntimeHints();
        new ConfirmationBinding().registerHints(hints, ConfirmationBindingTest.class.getClassLoader());
        return hints;
    }

    private void assertBound(Class<?> record) {
        assertThat(REFLECTION.onType(record)).as(record.getName()).accepts(hints);
        for (RecordComponent component : record.getRecordComponents()) {
            assertThat(REFLECTION.onMethodInvocation(component.getAccessor())).as(component.getAccessor().toString()).accepts(hints);
        }
    }
}
