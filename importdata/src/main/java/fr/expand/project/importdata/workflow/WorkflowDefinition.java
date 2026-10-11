package fr.expand.project.importdata.workflow;

import java.util.List;

/** Immutable independent extension of an object's metamodel. */
public record WorkflowDefinition(
        String id,
        String version,
        String label,
        String initialState,
        List<TypeRef> objectTypes,
        List<State> states,
        List<Transition> transitions) {
    public WorkflowDefinition {
        objectTypes = List.copyOf(objectTypes);
        states = List.copyOf(states);
        transitions = List.copyOf(transitions);
    }

    public record TypeRef(String name, boolean includeSubtypes) {}

    public record State(String code, String label, boolean terminal) {}

    public record Transition(String id, String from, String to, String label) {}

    public State state(String code) {
        return states.stream().filter(s -> s.code().equals(code)).findFirst().orElse(null);
    }

    public Transition transition(String id) {
        return transitions.stream().filter(t -> t.id().equals(id)).findFirst().orElse(null);
    }

    public List<Transition> transitionsFrom(String state) {
        return transitions.stream().filter(t -> t.from().equals(state)).toList();
    }
}
