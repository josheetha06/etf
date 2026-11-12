package com.epictalenttrust.hr.security;

import com.epictalenttrust.hr.activity.ActorKind;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Resolves who is acting right now, for audit columns and the audit_trail trigger.
 * Background work (AI screening, reminders) runs as SYSTEM unless told otherwise.
 */
public final class ActorContext {

    public record Actor(ActorKind kind, UUID id) {
        public static final Actor SYSTEM = new Actor(ActorKind.SYSTEM, null);
    }

    private static final ThreadLocal<Actor> OVERRIDE = new ThreadLocal<>();

    private ActorContext() {
    }

    public static Actor current() {
        Actor override = OVERRIDE.get();
        if (override != null) {
            return override;
        }
        return CurrentUser.find()
                .map(user -> new Actor(ActorKind.HR_USER, user.id()))
                .orElse(Actor.SYSTEM);
    }

    public static <T> T callAs(Actor actor, Supplier<T> work) {
        Actor previous = OVERRIDE.get();
        OVERRIDE.set(actor);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                OVERRIDE.remove();
            } else {
                OVERRIDE.set(previous);
            }
        }
    }

    public static void runAs(Actor actor, Runnable work) {
        callAs(actor, () -> {
            work.run();
            return null;
        });
    }
}
