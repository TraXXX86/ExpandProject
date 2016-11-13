//
// Ce fichier a été généré par l'implémentation de référence JavaTM Architecture for XML Binding (JAXB), v2.2.8-b130911.1802 
// Voir <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Toute modification apportée à ce fichier sera perdue lors de la recompilation du schéma source. 
// Généré le : 2016.11.13 à 07:00:13 PM CET 
//


package fr.expand.project.importdata.dto.generated;

import java.io.Serializable;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Classe Java pour DATAS element declaration.
 * 
 * <p>Le fragment de schéma suivant indique le contenu attendu figurant dans cette classe.
 * 
 * <pre>
 * &lt;element name="DATAS">
 *   &lt;complexType>
 *     &lt;complexContent>
 *       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *         &lt;sequence>
 *           &lt;element name="OBJECTS" type="{}OBJECTS"/>
 *           &lt;element name="LINKS" type="{}LINKS"/>
 *         &lt;/sequence>
 *       &lt;/restriction>
 *     &lt;/complexContent>
 *   &lt;/complexType>
 * &lt;/element>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "objects",
    "links"
})
@XmlRootElement(name = "DATAS")
public class DataPack
    implements Serializable
{

    private final static long serialVersionUID = 2L;
    @XmlElement(name = "OBJECTS", required = true)
    protected DataPackObjects objects;
    @XmlElement(name = "LINKS", required = true)
    protected DataPackLinks links;

    /**
     * Obtient la valeur de la propriété objects.
     * 
     * @return
     *     possible object is
     *     {@link DataPackObjects }
     *     
     */
    public DataPackObjects getOBJECTS() {
        return objects;
    }

    /**
     * Définit la valeur de la propriété objects.
     * 
     * @param value
     *     allowed object is
     *     {@link DataPackObjects }
     *     
     */
    public void setOBJECTS(DataPackObjects value) {
        this.objects = value;
    }

    /**
     * Obtient la valeur de la propriété links.
     * 
     * @return
     *     possible object is
     *     {@link DataPackLinks }
     *     
     */
    public DataPackLinks getLINKS() {
        return links;
    }

    /**
     * Définit la valeur de la propriété links.
     * 
     * @param value
     *     allowed object is
     *     {@link DataPackLinks }
     *     
     */
    public void setLINKS(DataPackLinks value) {
        this.links = value;
    }

}
