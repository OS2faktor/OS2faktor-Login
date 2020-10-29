package dk.digitalidentity.nemid.service.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "status",
    "pid",
    "cpr",
    "redirURL"
})
@XmlRootElement(name = "response")
public class PidResponse {
	private PidStatus status;
	private String pid;
	private String cpr;	
	private String redirURL;
}
