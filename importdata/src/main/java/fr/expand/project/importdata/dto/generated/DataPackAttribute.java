//
// Ce fichier a �t� g�n�r� par l'impl�mentation de r�f�rence JavaTM Architecture for XML Binding (JAXB), v2.2.11 
// Voir <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Toute modification apport�e � ce fichier sera perdue lors de la recompilation du sch�ma source. 
// G�n�r� le : 2017.02.12 � 09:54:21 PM CET 
//


package fr.expand.project.importdata.dto.generated;

import java.io.Serializable;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Classe Java pour ATTRIBUTE complex type.
 * 
 * <p>Le fragment de sch�ma suivant indique le contenu attendu figurant dans cette classe.
 * 
 * <pre>
 * &lt;complexType name="ATTRIBUTE"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;attribute name="KEY" use="required" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *       &lt;attribute name="VALUE" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ATTRIBUTE")
public class DataPackAttribute implements Serializable
{

    private final static long serialVersionUID = 2L;
    @XmlAttribute(name = "KEY", required = true)
    protected String key;
    @XmlAttribute(name = "VALUE")
    protected String value;

    /**
     * Default no-arg constructor
     * 
     */
    public DataPackAttribute() {
        super();
    }

    /**
     * Fully-initialising value constructor
     * 
     */
    public DataPackAttribute(final String key, final String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Obtient la valeur de la propri�t� key.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getKEY() {
        return key;
    }

    /**
     * D�finit la valeur de la propri�t� key.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setKEY(String value) {
        this.key = value;
    }

    /**
     * Obtient la valeur de la propri�t� value.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVALUE() {
        return value;
    }

    /**
     * D�finit la valeur de la propri�t� value.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVALUE(String value) {
        this.value = value;
    }

}
