package dk.digitalidentity.nemid.service.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "response",
    "request"
})
@XmlRootElement(name = "method")
public class PidMethod {	
	private PidResponse response;
	private PidRequest request = new PidRequest();

    @XmlAttribute(name = "name")
	private String name = "pidCprRequest";
    
    @XmlAttribute(name = "version")
	private String version = "1.0";
}
