package fr.expand.project.importdata.workflow;

import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class WorkflowXmlTest {
    static String example() throws Exception {
        try (var stream =
                WorkflowXmlTest.class.getResourceAsStream("/workflow/example-workflow.xml")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void rejects(String xml) {
        assertThrows(IllegalArgumentException.class, () -> WorkflowXml.parse(xml));
    }

    @Test
    public void parsesIndependentDefinitionAndTransitions() throws Exception {
        var d = WorkflowXml.parse(example());
        assertEquals("review", d.id());
        assertEquals("1", d.version());
        assertEquals(4, d.states().size());
        assertEquals("review", d.transition("submit").to());
        assertTrue(d.state("archived").terminal());
        assertEquals(2, d.transitionsFrom("review").size());
        assertFalse(d.objectTypes().get(0).includeSubtypes());
    }

    @Test
    public void rejectsExternalEntitiesUnknownFieldsAndOversizedXml() throws Exception {
        rejects(
                example()
                        .replace(
                                "<WORKFLOW",
                                "<!DOCTYPE WORKFLOW [<!ENTITY x SYSTEM"
                                        + " 'file:///etc/passwd'>]><WORKFLOW"));
        rejects(example().replace("INITIAL_STATE=", "UNKNOWN=\"x\" INITIAL_STATE="));
        rejects(" ".repeat(WorkflowXml.MAX_BYTES + 1));
    }

    @Test
    public void validatesUniqueAndReachableStatesAndReferences() throws Exception {
        rejects(example().replace("CODE=\"approved\"", "CODE=\"draft\""));
        rejects(example().replace("TO=\"approved\"", "TO=\"missing\""));
        rejects(example().replace("INITIAL_STATE=\"draft\"", "INITIAL_STATE=\"missing\""));
        rejects(
                example()
                        .replace(
                                "<STATE CODE=\"approved\"",
                                "<STATE CODE=\"unreachable\" LABEL=\"Unreachable\"/><STATE"
                                        + " CODE=\"approved\""));
        rejects(example().replace("ID=\"approve\"", "ID=\"submit\""));
    }

    @Test
    public void rejectsTerminalOutgoingAndReservedSentinel() throws Exception {
        rejects(
                example()
                        .replace(
                                "CODE=\"draft\" LABEL=\"Brouillon\"",
                                "CODE=\"draft\" LABEL=\"Brouillon\" TERMINAL=\"true\""));
        rejects(example().replace("draft", "__unassigned__"));
    }
}
