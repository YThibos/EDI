package io.github.ythibos.edi.invariant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("4.3 Dispatch and effort")
class DispatchAndEffortTest {

    @Nested
    @DisplayName("I-14 Routing by written category, three effort levels, high is the ceiling")
    class RoutingByWrittenCategory {

        @Test
        @DisplayName("AT-14 the loader rejects xhigh, max, ultra, a category without why, a weaker researcher and a launch without model and effort")
        void theLoaderRejectsInvalidDispatchRules() {
            Pending.until(Slice.S3);
        }
    }

    @Nested
    @DisplayName("I-15 EDI work runs on Opus 5.5 high")
    class EdiWorkRunsOnTheProjectOverride {

        @Test
        @DisplayName("AT-15 a task on the EDI project resolves to the project override regardless of category")
        void anEdiTaskResolvesToTheProjectOverride() {
            Pending.until(Slice.S3);
        }
    }

    @Nested
    @DisplayName("I-16 A relaunch re-routes")
    class ARelaunchReRoutes {

        @Test
        @DisplayName("AT-16 after a category's model changes, relaunching a dead worker uses the new model")
        void aRelaunchUsesTheCategorysCurrentModel() {
            Pending.until(Slice.S3);
        }
    }

    @Nested
    @DisplayName("I-17 Quality is never traded for cost")
    class QualityIsNeverTradedForCost {

        @Test
        @DisplayName("AT-17 the dispatch resolver takes no budget input")
        void theResolverHasNoBudgetInput() {
            Pending.until(Slice.S3);
        }
    }
}
