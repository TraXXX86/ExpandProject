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
 * <p>Classe Java pour OBJ_LINK complex type.
 * 
 * <p>Le fragment de sch�ma suivant indique le contenu attendu figurant dans cette classe.
 * 
 * <pre>
 * &lt;complexType name="OBJ_LINK"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{}NODE"&gt;
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
@XmlType(name = "OBJ_LINK")
public class DataPackObjectLink
    extends AbstractDataPackObject
    implements Serializable
{

    private final static long serialVersionUID = 2L;
    @XmlAttribute(name = "ID", required = true)
    protected int id;
    @XmlAttribute(name = "TYPE")
    protected String type;

    /**
     * Default no-arg constructor
     * 
     */
    public DataPackObjectLink() {
        super();
    }

    /**
     * Fully-initialising value constructor
     * 
     */
    public DataPackObjectLink(final int id, final String type) {
        super();
        this.id = id;
        this.type = type;
    }

    /**
     * Obtient la valeur de la propri�t� id.
     * 
     */
    public int getID() {
        return id;
    }

    /**
     * D�finit la valeur de la propri�t� id.
     * 
     */
    public void setID(int value) {
        this.id = value;
    }

    /**
     * Obtient la valeur de la propri�t� type.
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
     * D�finit la valeur de la propri�t� type.
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
