//
// Ce fichier a été généré par l'implémentation de référence JavaTM Architecture for XML Binding (JAXB), v2.2.11 
// Voir <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Toute modification apportée à ce fichier sera perdue lors de la recompilation du schéma source. 
// Généré le : 2017.02.12 à 09:54:21 PM CET 
//


package fr.expand.project.importdata.dto.generated;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Classe Java pour OBJECT complex type.
 * 
 * <p>Le fragment de schéma suivant indique le contenu attendu figurant dans cette classe.
 * 
 * <pre>
 * &lt;complexType name="OBJECT"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{}NODE"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="ATTRIBUTE" type="{}ATTRIBUTE" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *       &lt;attribute name="ID" use="required" type="{http://www.w3.org/2001/XMLSchema}int" /&gt;
 *       &lt;attribute name="TYPE" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "OBJECT", propOrder = {
    "attribute"
})
public class DataPackObject
    extends AbstractDataPackObject
    implements Serializable
{

    private final static long serialVersionUID = 2L;
    @XmlElement(name = "ATTRIBUTE")
    protected List<DataPackAttribute> attribute;
    @XmlAttribute(name = "ID", required = true)
    protected int id;
    @XmlAttribute(name = "TYPE")
    protected String type;

    /**
     * Default no-arg constructor
     * 
     */
    public DataPackObject() {
        super();
    }

    /**
     * Fully-initialising value constructor
     * 
     */
    public DataPackObject(final List<DataPackAttribute> attribute, final int id, final String type) {
        super();
        this.attribute = attribute;
        this.id = id;
        this.type = type;
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
     * Obtient la valeur de la propriété id.
     * 
     */
    public int getID() {
        return id;
    }

    /**
     * Définit la valeur de la propriété id.
     * 
     */
    public void setID(int value) {
        this.id = value;
    }

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

}
