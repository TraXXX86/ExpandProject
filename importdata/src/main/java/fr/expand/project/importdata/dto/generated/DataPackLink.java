//
// Ce fichier a été généré par l'implémentation de référence JavaTM Architecture for XML Binding (JAXB), v2.2.11 
// Voir <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Toute modification apportée à ce fichier sera perdue lors de la recompilation du schéma source. 
// Généré le : 2016.11.13 à 08:33:41 PM CET 
//


package fr.expand.project.importdata.dto.generated;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Classe Java pour LINK complex type.
 * 
 * <p>Le fragment de schéma suivant indique le contenu attendu figurant dans cette classe.
 * 
 * <pre>
 * &lt;complexType name="LINK"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="TYPE" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="ATTRIBUTE" type="{}ATTRIBUTE" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="OBJ_LINK_A" type="{}OBJ_LINK"/&gt;
 *         &lt;element name="OBJ_LINK_B" type="{}OBJ_LINK"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "LINK", propOrder = {
    "type",
    "attribute",
    "objlinka",
    "objlinkb"
})
public class DataPackLink
    implements Serializable
{

    private final static long serialVersionUID = 2L;
    @XmlElement(name = "TYPE", required = true)
    protected String type;
    @XmlElement(name = "ATTRIBUTE")
    protected List<DataPackAttribute> attribute;
    @XmlElement(name = "OBJ_LINK_A", required = true)
    protected DataPackObjectLink objlinka;
    @XmlElement(name = "OBJ_LINK_B", required = true)
    protected DataPackObjectLink objlinkb;

    /**
     * Obtient la valeur de la propriété type.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTYPE() {
        return type;
    }

    /**
     * Définit la valeur de la propriété type.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTYPE(String value) {
        this.type = value;
    }

    /**
     * Gets the value of the attribute property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the attribute property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getATTRIBUTE().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link DataPackAttribute }
     * 
     * 
     */
    public List<DataPackAttribute> getATTRIBUTE() {
        if (attribute == null) {
            attribute = new ArrayList<DataPackAttribute>();
        }
        return this.attribute;
    }

    /**
     * Obtient la valeur de la propriété objlinka.
     * 
     * @return
     *     possible object is
     *     {@link DataPackObjectLink }
     *     
     */
    public DataPackObjectLink getOBJLINKA() {
        return objlinka;
    }

    /**
     * Définit la valeur de la propriété objlinka.
     * 
     * @param value
     *     allowed object is
     *     {@link DataPackObjectLink }
     *     
     */
    public void setOBJLINKA(DataPackObjectLink value) {
        this.objlinka = value;
    }

    /**
     * Obtient la valeur de la propriété objlinkb.
     * 
     * @return
     *     possible object is
     *     {@link DataPackObjectLink }
     *     
     */
    public DataPackObjectLink getOBJLINKB() {
        return objlinkb;
    }

    /**
     * Définit la valeur de la propriété objlinkb.
     * 
     * @param value
     *     allowed object is
     *     {@link DataPackObjectLink }
     *     
     */
    public void setOBJLINKB(DataPackObjectLink value) {
        this.objlinkb = value;
    }

}
