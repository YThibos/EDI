package io.github.ythibos.edi.domain.publish;

public record TextFinding(PublishRule rule, String place, String reason) {

    @Override
    public String toString() {
        return place + ": " + reason;
    }
}
