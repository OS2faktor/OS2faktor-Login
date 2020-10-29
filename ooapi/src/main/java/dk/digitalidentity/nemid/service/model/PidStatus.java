package dk.digitalidentity.nemid.service.model;

import java.util.List;

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
	"statusText"
})
@XmlRootElement(name = "status")
public class PidStatus {
	
    @XmlAttribute(name = "statusCode")
	private String statusCode;
    
    private List<String> statusText;
}
