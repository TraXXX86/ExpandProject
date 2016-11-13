package fr.expand.project.importdata.dao;

import fr.expand.project.commons.ObjectTypeEnum;
import fr.expand.project.importdata.dto.generated.DataPackObject;

public abstract class IConnectorDb {

	/**
	 * Constructor
	 */
	public IConnectorDb() {
		super();
		this.connectToDb();
	}

	/**
	 * Override finalize to always close connection
	 */
	@Override
	protected void finalize() throws Throwable {
		this.closeConnection();
		super.finalize();
	}

	/**
	 * Create connection to DB
	 */
	protected abstract void connectToDb();

	/**
	 * Close connection to DB
	 */
	protected abstract void closeConnection();

	/**
	 * Create a simple object
	 * 
	 * @param object
	 * @return ID of new object
	 */
	public abstract int writeObject(DataPackObject object);

	/**
	 * Create a simple link between 2 objects
	 * 
	 * @param objectA
	 * @param objectB
	 * @param isOriented
	 *            : true if link is oriented objectA to objectB
	 * @return
	 */
	public abstract int writeLink(DataPackObject objectA, DataPackObject objectB, boolean isOriented);

	/**
	 * Get object from DB
	 * 
	 * @param typeObject
	 * @param idObject
	 * @return
	 */
	public abstract DataPackObject getObjectToDbDto(ObjectTypeEnum typeObject, int idObject);
}
