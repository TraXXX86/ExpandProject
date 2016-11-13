//
// Ce fichier a été généré par l'implémentation de référence JavaTM Architecture for XML Binding (JAXB), v2.2.11 
// Voir <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Toute modification apportée à ce fichier sera perdue lors de la recompilation du schéma source. 
// Généré le : 2016.11.13 à 08:33:41 PM CET 
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
 * &lt;element name="DATAS"&gt;
 *   &lt;complexType&gt;
 *     &lt;complexContent&gt;
 *       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *         &lt;sequence&gt;
 *           &lt;element name="OBJECTS" type="{}OBJECTS"/&gt;
 *           &lt;element name="LINKS" type="{}LINKS"/&gt;
 *         &lt;/sequence&gt;
 *       &lt;/restriction&gt;
 *     &lt;/complexContent&gt;
 *   &lt;/complexType&gt;
 * &lt;/element&gt;
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
