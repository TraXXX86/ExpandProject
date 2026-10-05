package fr.expand.project.importdata.api.impl;

import fr.expand.project.importdata.audit.AuditActor;
import fr.expand.project.importdata.dao.IConnectorDb;
import fr.expand.project.importdata.dao.connectors.impl.CypherConnector;
import fr.expand.project.importdata.dto.generated.DATAS;
import fr.expand.project.importdata.model.*;
import fr.expand.project.importdata.validation.*;
import fr.expand.project.importdata.xml.XmlSupport;

import jakarta.xml.bind.JAXBException;

import java.io.File;
import java.util.Objects;

/** Request-scoped model validation followed by one atomic data transaction. */
public class ModelBasedImportAPI implements AutoCloseable {
    private final ModelManager modelManager;
    private final AuditActor actor;
    private final DataValidator validator;
    private IConnectorDb connector;

    public ModelBasedImportAPI() {
        this(ModelManager.getInstance());
    }

    public ModelBasedImportAPI(ModelManager manager) {
        this(manager, AuditActor.system());
    }

    public ModelBasedImportAPI(ModelManager manager, AuditActor actor) {
        this.actor = Objects.requireNonNull(actor);
        modelManager = Objects.requireNonNull(manager, "modelManager");
        validator = new DataValidator(manager);
    }

    public ValidationResult importData(File file, boolean validateOnly) throws JAXBException {
        return importData(XmlSupport.parseData(file), validateOnly);
    }

    public ValidationResult importData(DATAS data, boolean validateOnly) {
        return importData(data, validateOnly, null);
    }

    public ValidationResult importData(DATAS data, boolean validateOnly, String modelKey) {
        ValidationResult result = validator.validate(data);
        if (!result.isValid() || validateOnly) return result;
        if (modelKey == null || modelKey.isBlank()) {
            try (Neo4jModelStore store = new Neo4jModelStore(actor)) {
                modelKey =
                        store.storeModel(
                                modelManager.getCurrentModel(), modelManager.getCurrentModelXml());
            }
        }
        if (connector == null) connector = new CypherConnector(actor);
        connector.setModelKey(modelKey);
        connector.importData(data, modelKey, modelManager);
        return result;
    }

    public void setConnector(IConnectorDb connector) {
        if (this.connector != null) this.connector.close();
        this.connector = Objects.requireNonNull(connector);
    }

    @Override
    public void close() {
        if (connector != null) connector.close();
    }
}
