package io.github.ythibos.edi.domain.personality;

import java.util.List;
import java.util.Optional;

public record Addressing(Level formality) {

    private static final double SHARE_OF_FORMALITY = 0.6;
    private static final int BRIDGE_REGISTER_FROM = 8;
    private static final int CLOSING_TAG_PREFERRED_UP_TO = 3;

    static Addressing at(Level formality) {
        return new Addressing(formality);
    }

    public Likelihood in(MessageContext context) {
        return Likelihood.of(chanceIn(context));
    }

    public boolean isNever() {
        return formality.equals(Level.LOWEST);
    }

    public List<AddressPlacement> places() {
        if (isNever()) {
            return List.of();
        }
        if (formality.value() >= BRIDGE_REGISTER_FROM) {
            return List.of(AddressPlacement.OPENING_THE_MESSAGE, AddressPlacement.OPENING_A_LATER_SENTENCE);
        }
        return List.of(AddressPlacement.OPENING_THE_MESSAGE, AddressPlacement.OPENING_A_LATER_SENTENCE, AddressPlacement.CLOSING_TAG);
    }

    public Optional<AddressPlacement> preferredPlace() {
        return isNever() || formality.value() > CLOSING_TAG_PREFERRED_UP_TO ? Optional.empty() : Optional.of(AddressPlacement.CLOSING_TAG);
    }

    private double chanceIn(MessageContext context) {
        double chanceInAPlainMessage = SHARE_OF_FORMALITY * formality.shareOfTheHighest();
        return 1 - Math.pow(1 - chanceInAPlainMessage, context.weight());
    }
}
