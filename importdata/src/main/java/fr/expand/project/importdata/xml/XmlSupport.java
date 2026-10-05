package fr.expand.project.importdata.xml;

import fr.expand.project.importdata.dto.generated.DATAS;
import fr.expand.project.importdata.model.generated.DATAMODEL;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

/** Schema-validated XML input with no DTDs, external entities or external schemas. */
public final class XmlSupport {
    private XmlSupport() {}

    private static final class Bindings {
        static final JAXBContext MODEL = context(DATAMODEL.class);
        static final JAXBContext DATA = context(DATAS.class);
        static final Schema MODEL_SCHEMA = schema("model/model.xsd");
        static final Schema DATA_SCHEMA = schema("datapack/data.xsd");
    }

    public static DATAMODEL parseModel(String xml) throws JAXBException {
        return parse(xml, Bindings.MODEL, Bindings.MODEL_SCHEMA, DATAMODEL.class);
    }

    public static DATAS parseData(String xml) throws JAXBException {
        return parse(xml, Bindings.DATA, Bindings.DATA_SCHEMA, DATAS.class);
    }

    public static DATAS parseData(File file) throws JAXBException {
        try {
            return parseData(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new JAXBException("Unable to read data XML", e);
        }
    }

    private static <T> T parse(String xml, JAXBContext context, Schema schema, Class<T> type)
            throws JAXBException {
        if (xml == null || xml.isBlank()) {
            throw new JAXBException("XML content is empty");
        }
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            XMLReader reader = factory.newSAXParser().getXMLReader();
            reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            reader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            reader.setEntityResolver(
                    (publicId, systemId) -> {
                        throw new SAXException("External XML entities are disabled");
                    });
            Unmarshaller unmarshaller = context.createUnmarshaller();
            unmarshaller.setSchema(schema);
            unmarshaller.setEventHandler(event -> false);
            return type.cast(
                    unmarshaller.unmarshal(
                            new SAXSource(reader, new InputSource(new StringReader(xml)))));
        } catch (JAXBException e) {
            // JAXB's UnmarshalException may carry only a linked SAX exception and a
            // null message. Give callers a stable validation failure to report.
            throw new JAXBException("Invalid XML content", e);
        } catch (Exception e) {
            throw new JAXBException("Invalid XML", e);
        }
    }

    private static JAXBContext context(Class<?> type) {
        try {
            return JAXBContext.newInstance(type);
        } catch (JAXBException e) {
            throw new IllegalStateException("Cannot initialize XML bindings", e);
        }
    }

    private static Schema schema(String path) {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try (InputStream input = XmlSupport.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled schema: " + path);
            }
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newSchema(new StreamSource(input));
        } catch (SAXException | IOException e) {
            throw new IllegalStateException("Cannot load bundled schema: " + path, e);
        }
    }
}
