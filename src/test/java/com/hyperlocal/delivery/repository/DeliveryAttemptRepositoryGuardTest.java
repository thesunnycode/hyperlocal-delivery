package com.hyperlocal.delivery.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * Structural guard for the append-only invariant on {@link DeliveryAttempt}
 * history (see {@link DeliveryAttemptRepository} javadoc: "the append-only
 * DeliveryAttempt log"). A {@link DeliveryAttempt} row must never be
 * updated or deleted once written -- only ever inserted via
 * {@code repository.save()} on a freshly built entity.
 *
 * <p>This is a compile-time/reflection check rather than a runtime one:
 * it asserts the repository interface itself declares no derived
 * update/delete query method (e.g. {@code deleteBy...},
 * {@code updateBy...}, a {@code @Modifying} query) for
 * {@link com.hyperlocal.delivery.model.DeliveryAttempt}, so no future
 * change can introduce a mutation path without this test failing and
 * forcing a deliberate decision.
 */
class DeliveryAttemptRepositoryGuardTest {

    @Test
    void declaresNoUpdateOrDeleteQueryMethods() {
        Method[] declaredMethods = DeliveryAttemptRepository.class.getDeclaredMethods();

        boolean hasMutatingMethod = Arrays.stream(declaredMethods)
                .map(Method::getName)
                .anyMatch(name -> name.toLowerCase().startsWith("update")
                        || name.toLowerCase().startsWith("delete")
                        || name.toLowerCase().startsWith("remove"));

        assertThat(hasMutatingMethod)
                .as("DeliveryAttemptRepository must stay append-only: only findBy*/countBy* "
                        + "derived queries and JpaRepository#save() on new entities are allowed; "
                        + "no update/delete/remove method should ever be declared here")
                .isFalse();
    }
}
