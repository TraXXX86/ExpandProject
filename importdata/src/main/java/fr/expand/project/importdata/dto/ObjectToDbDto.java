package fr.expand.project.importdata.dto;

import java.util.HashMap;
import java.util.Map;

import fr.expand.project.commons.ObjectTypeEnum;

public class ObjectToDbDto {

	private int id = -1;

	private ObjectTypeEnum type;

	private Map<String, Object> attributes = new HashMap<>();

	// ################################# Constructors

	public ObjectToDbDto(ObjectTypeEnum type) {
		super();
		this.type = type;
	}

	// ################################# Default Getters and setters

	/**
	 * Object ID
	 * 
	 * @return
	 */
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	/**
	 * Object Type
	 * 
	 * @return
	 */
	public ObjectTypeEnum getType() {
		return type;
	}

	public void setType(ObjectTypeEnum type) {
		this.type = type;
	}

	/**
	 * Object attributes
	 * 
	 * @return
	 */
	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public void setAttributes(Map<String, Object> attributes) {
		this.attributes = attributes;
	}

}
