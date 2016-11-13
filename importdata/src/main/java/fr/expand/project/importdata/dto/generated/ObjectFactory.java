//
// Ce fichier a été généré par l'implémentation de référence JavaTM Architecture for XML Binding (JAXB), v2.2.11 
// Voir <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Toute modification apportée à ce fichier sera perdue lors de la recompilation du schéma source. 
// Généré le : 2016.11.13 à 09:27:38 PM CET 
//


package fr.expand.project.importdata.dto.generated;

import javax.xml.bind.annotation.XmlRegistry;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the fr.expand.project.importdata.dto.generated package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {


    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: fr.expand.project.importdata.dto.generated
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link DataPack }
     * 
     */
    public DataPack createDataPack() {
        return new DataPack();
    }

    /**
     * Create an instance of {@link DataPackObjects }
     * 
     */
    public DataPackObjects createDataPackObjects() {
        return new DataPackObjects();
    }

    /**
     * Create an instance of {@link DataPackObject }
     * 
     */
    public DataPackObject createDataPackObject() {
        return new DataPackObject();
    }

    /**
     * Create an instance of {@link DataPackAttribute }
     * 
     */
    public DataPackAttribute createDataPackAttribute() {
        return new DataPackAttribute();
    }

    /**
     * Create an instance of {@link DataPackLinks }
     * 
     */
    public DataPackLinks createDataPackLinks() {
        return new DataPackLinks();
    }

    /**
     * Create an instance of {@link DataPackLink }
     * 
     */
    public DataPackLink createDataPackLink() {
        return new DataPackLink();
    }

    /**
     * Create an instance of {@link DataPackObjectLink }
     * 
     */
    public DataPackObjectLink createDataPackObjectLink() {
        return new DataPackObjectLink();
    }

}
