/**
 * $Id: AttributeType.java,v 1.3 2005/09/06 18:06:33 wuttke Exp $
 * 
 * @author Matthias Wuttke
 * @version $Revision: 1.3 $
 */
package org.tinyradius.dictionary;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.tinyradius.attribute.RadiusAttribute;

/**
 * Represents a Radius attribute type.
 */
public class AttributeType {

	/**
	 * Create a new attribute type.
	 * 
	 * @param code
	 *            Radius attribute type code
	 * @param name
	 *            Attribute type name
	 * @param type
	 *            RadiusAttribute descendant who handles
	 *            attributes of this type
	 */
	public AttributeType(int code, String name, Class<? extends RadiusAttribute> type) {
		setTypeCode(code);
		setName(name);
		setAttributeClass(type);
	}

	/**
	 * Constructs a Vendor-Specific sub-attribute type.
	 * 
	 * @param vendor
	 *            vendor ID
	 * @param code
	 *            sub-attribute type code
	 * @param name
	 *            sub-attribute name
	 * @param type
	 *            sub-attribute class
	 */
	public AttributeType(int vendor, int code, String name, Class<? extends RadiusAttribute> type) {
		setTypeCode(code);
		setName(name);
		setAttributeClass(type);
		setVendorId(vendor);
	}

	/**
	 * Retrieves the Radius type code for this attribute type.
	 * 
	 * @return Radius type code
	 */
	public int getTypeCode() {
		return typeCode;
	}

	/**
	 * Sets the Radius type code for this attribute type.
	 * 
	 * @param code
	 *            type code, 1-255
	 */
	public void setTypeCode(int code) {
		if (code < 1 || code > 255) {
			throw new IllegalArgumentException("code out of bounds");
		}
		
		this.typeCode = code;
	}

	/**
	 * Retrieves the name of this type.
	 * 
	 * @return name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name of this type.
	 * 
	 * @param name
	 *            type name
	 */
	public void setName(String name) {
		if (name == null || name.length() == 0) {
			throw new IllegalArgumentException("name is empty");
		}
		
		this.name = name;
	}

	/**
	 * Retrieves the RadiusAttribute descendant class which represents
	 * attributes of this type.
	 * 
	 * @return class
	 */
	public Class<? extends RadiusAttribute> getAttributeClass() {
		return attributeClass;
	}

	/**
	 * Sets the RadiusAttribute descendant class which represents
	 * attributes of this type.
	 */
	public void setAttributeClass(Class<? extends RadiusAttribute> type) {
		if (type == null) {
			throw new NullPointerException("type is null");
		}

		this.attributeClass = type;
	}

	/**
	 * Returns the vendor ID.
	 * No vendor specific attribute = -1
	 * 
	 * @return vendor ID
	 */
	public int getVendorId() {
		return vendorId;
	}

	/**
	 * Sets the vendor ID.
	 * 
	 * @param vendorId
	 *            vendor ID
	 */
	public void setVendorId(int vendorId) {
		this.vendorId = vendorId;
	}

	/**
	 * Returns the name of the given integer value if this attribute
	 * is an enumeration, or null if it is not or if the integer value
	 * is unknown.
	 * 
	 * @return name
	 */
	public String getEnumeration(int value) {
		if (enumeration != null) {
			return (String) enumeration.get(Integer.valueOf(value));
		}

		return null;
	}

	/**
	 * Returns the number of the given string value if this attribute is
	 * an enumeration, or null if it is not or if the string value is unknown.
	 * 
	 * @param value
	 *            string value
	 * @return Integer or null
	 */
	public Integer getEnumeration(String value) {
		if (value == null || value.length() == 0) {
			throw new IllegalArgumentException("value is empty");
		}

		if (enumeration == null) {
			return null;
		}
		
		for (Iterator<Map.Entry<Integer, String>> i = enumeration.entrySet().iterator(); i.hasNext();) {
			Map.Entry<Integer, String> e = i.next();
			
			if (e.getValue().equals(value)) {
				return e.getKey();
			}
		}

		return null;
	}

	/**
	 * Adds a name for an integer value of this attribute.
	 * 
	 * @param num
	 *            number that shall get a name
	 * @param name
	 *            the name for this number
	 */
	public void addEnumerationValue(int num, String name) {
		if (name == null || name.length() == 0) {
			throw new IllegalArgumentException("name is empty");
		}
		
		if (enumeration == null) {
			enumeration = new HashMap<Integer, String>();
		}

		enumeration.put(Integer.valueOf(num), name);
	}

	/**
	 * String representation of AttributeType object
	 * for debugging purposes.
	 * 
	 * @return string
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		String s = getTypeCode() + "/" + getName() + ": " + attributeClass.getName();
		if (getVendorId() != -1) {
			s += " (vendor " + getVendorId() + ")";
		}
		
		return s;
	}

	private int vendorId = -1;
	private int typeCode;
	private String name;
	private Class<? extends RadiusAttribute> attributeClass;
	private Map<Integer, String> enumeration = null;
}
