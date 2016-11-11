package fr.expand.project.importdata.dao;

import fr.expand.project.importdata.ObjectTypeEnum;
import fr.expand.project.importdata.dto.ObjectToDbDto;

public interface IConnectorDb {

	/**
	 * Create a simple object
	 * 
	 * @param object
	 * @return ID of new object
	 */
	public int writeObject(ObjectToDbDto object);

	/**
	 * Create a simple link between 2 objects
	 * 
	 * @param objectA
	 * @param objectB
	 * @param isOriented
	 *            : true if link is oriented objectA to objectB
	 * @return
	 */
	public int writeLink(ObjectToDbDto objectA, ObjectToDbDto objectB, boolean isOriented);

	/**
	 * Get object from DB
	 * 
	 * @param typeObject
	 * @param idObject
	 * @return
	 */
	public ObjectToDbDto getObjectToDbDto(ObjectTypeEnum typeObject, int idObject);
}
