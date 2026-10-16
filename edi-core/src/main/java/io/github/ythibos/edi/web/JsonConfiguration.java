package io.github.ythibos.edi.web;

import java.nio.file.Path;
import java.util.function.Function;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.worker.DoneCheck;
import io.github.ythibos.edi.domain.worker.PaneState;
import io.github.ythibos.edi.domain.worker.WorkerActivity;
import io.github.ythibos.edi.domain.worker.WorkerId;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Configuration(proxyBeanMethods = false)
class JsonConfiguration {

    @Bean
    JsonMapperBuilderCustomizer domainValuesAsText() {
        return builder -> builder.addModule(domainValues());
    }

    private static SimpleModule domainValues() {
        return new SimpleModule("edi-domain-values")
                .addSerializer(TaskId.class, ToStringSerializer.instance)
                .addSerializer(ProjectName.class, ToStringSerializer.instance)
                .addSerializer(ProjectKey.class, ToStringSerializer.instance)
                .addSerializer(WorkKey.class, ToStringSerializer.instance)
                .addSerializer(BaseBranch.class, ToStringSerializer.instance)
                .addSerializer(DecisionNumber.class, ToStringSerializer.instance)
                .addSerializer(OptionKey.class, ToStringSerializer.instance)
                .addSerializer(Actor.class, ToStringSerializer.instance)
                .addSerializer(Path.class, ToStringSerializer.instance)
                .addSerializer(ModelId.class, ToStringSerializer.instance)
                .addSerializer(CategoryId.class, ToStringSerializer.instance)
                .addSerializer(BranchName.class, ToStringSerializer.instance)
                .addSerializer(WorkerId.class, ToStringSerializer.instance)
                .addSerializer(StatusKey.class, ToStringSerializer.instance)
                .addSerializer(MergeRequestAddress.class, ToStringSerializer.instance)
                .addSerializer(ForgeRepository.class, ToStringSerializer.instance)
                .addSerializer(MergeRequestState.class, new AsText<>(MergeRequestState.class, MergeRequestState::word))
                .addSerializer(EffortLevel.class, new AsText<>(EffortLevel.class, EffortLevel::word))
                .addSerializer(StatusState.class, new AsText<>(StatusState.class, StatusState::word))
                .addSerializer(WorkerActivity.class, new AsText<>(WorkerActivity.class, WorkerActivity::word))
                .addSerializer(DoneCheck.class, new AsText<>(DoneCheck.class, DoneCheck::word))
                .addSerializer(PaneState.class, new AsText<>(PaneState.class, PaneState::word))
                .addSerializer(TaskKind.class, new AsText<>(TaskKind.class, TaskKind::word))
                .addSerializer(TaskState.class, new AsText<>(TaskState.class, TaskState::word))
                .addSerializer(HoldKind.class, new AsText<>(HoldKind.class, HoldKind::word))
                .addSerializer(AnswerChannel.class, new AsText<>(AnswerChannel.class, AnswerChannel::word))
                .addSerializer(HoldUntil.class, new AsText<>(HoldUntil.class, JsonConfiguration::dayOrCaptainsWord));
    }

    private static String dayOrCaptainsWord(HoldUntil until) {
        return switch (until) {
            case HoldUntil.Day day -> day.date().toString();
            case HoldUntil.CaptainsWord _ -> "captain's word";
        };
    }

    private static final class AsText<T> extends StdSerializer<T> {

        private final Function<T, String> text;

        AsText(Class<T> type, Function<T, String> text) {
            super(type);
            this.text = text;
        }

        @Override
        public void serialize(T value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(text.apply(value));
        }
    }
}
