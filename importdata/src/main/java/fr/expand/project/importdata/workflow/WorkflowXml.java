package fr.expand.project.importdata.workflow;

import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

/** Strict, bounded and external-entity-free workflow XML parser. */
public final class WorkflowXml {
    public static final int MAX_BYTES = 1024 * 1024;
    private static final Schema SCHEMA = schema();

    private WorkflowXml() {}

    private static Schema schema() {
        try {
            var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newSchema(
                    Objects.requireNonNull(
                            WorkflowXml.class.getResource("/workflow/workflow.xsd")));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static WorkflowDefinition parse(String xml) {
        if (xml == null || xml.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Workflow XML required, maximum 1 MiB");
        try {
            var f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            f.setSchema(SCHEMA);
            var builder = f.newDocumentBuilder();
            builder.setErrorHandler(
                    new DefaultHandler() {
                        @Override
                        public void error(SAXParseException e) throws SAXParseException {
                            throw e;
                        }

                        @Override
                        public void fatalError(SAXParseException e) throws SAXParseException {
                            throw e;
                        }
                    });
            Element root =
                    builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
            var types = new ArrayList<WorkflowDefinition.TypeRef>();
            var states = new ArrayList<WorkflowDefinition.State>();
            var transitions = new ArrayList<WorkflowDefinition.Transition>();
            for (Element e : elements(root, "TYPE_REF"))
                types.add(
                        new WorkflowDefinition.TypeRef(
                                e.getAttribute("NAME"), bool(e, "INCLUDE_SUBTYPES")));
            for (Element e : elements(root, "STATE"))
                states.add(
                        new WorkflowDefinition.State(
                                e.getAttribute("CODE"),
                                e.getAttribute("LABEL"),
                                bool(e, "TERMINAL")));
            for (Element e : elements(root, "TRANSITION"))
                transitions.add(
                        new WorkflowDefinition.Transition(
                                e.getAttribute("ID"),
                                e.getAttribute("FROM"),
                                e.getAttribute("TO"),
                                e.getAttribute("LABEL")));
            var definition =
                    new WorkflowDefinition(
                            root.getAttribute("ID"),
                            root.getAttribute("VERSION"),
                            root.getAttribute("LABEL"),
                            root.getAttribute("INITIAL_STATE"),
                            types,
                            states,
                            transitions);
            validate(definition);
            return definition;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid workflow XML: " + e.getMessage(), e);
        }
    }

    private static boolean bool(Element e, String name) {
        return Set.of("true", "1").contains(e.getAttribute(name));
    }

    private static List<Element> elements(Element root, String name) {
        var result = new ArrayList<Element>();
        var list = root.getElementsByTagName(name);
        for (int i = 0; i < list.getLength(); i++) result.add((Element) list.item(i));
        return result;
    }

    private static void validate(WorkflowDefinition d) {
        var types = new HashSet<String>();
        for (var t : d.objectTypes())
            if (!types.add(t.name()))
                throw new IllegalArgumentException("Duplicate object type: " + t.name());
        var codes = new HashSet<String>();
        for (var s : d.states())
            if (s.code().startsWith("__") || !codes.add(s.code()))
                throw new IllegalArgumentException("Duplicate state: " + s.code());
        if (!codes.contains(d.initialState()))
            throw new IllegalArgumentException("Unknown initial state");
        var ids = new HashSet<String>();
        for (var t : d.transitions()) {
            if (!ids.add(t.id()))
                throw new IllegalArgumentException("Duplicate transition: " + t.id());
            if (!codes.contains(t.from()) || !codes.contains(t.to()))
                throw new IllegalArgumentException("Unknown transition state");
            if (d.state(t.from()).terminal())
                throw new IllegalArgumentException(
                        "Terminal state has outgoing transition: " + t.from());
        }
        var reached = new HashSet<String>();
        reached.add(d.initialState());
        boolean changed;
        do {
            changed = false;
            for (var t : d.transitions())
                if (reached.contains(t.from())) changed |= reached.add(t.to());
        } while (changed);
        if (!reached.equals(codes))
            throw new IllegalArgumentException(
                    "All states must be reachable from the initial state");
    }
}
