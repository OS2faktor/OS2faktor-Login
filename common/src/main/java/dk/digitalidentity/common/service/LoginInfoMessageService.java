package dk.digitalidentity.common.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.LoginInfoMessageDao;
import dk.digitalidentity.common.dao.model.LoginInfoMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LoginInfoMessageService {

	@Autowired
	private LoginInfoMessageDao loginInfoMessageDao;

	public LoginInfoMessage getInfobox() {
		List<LoginInfoMessage> all = loginInfoMessageDao.findAll();

		if (all.size() == 0) {
			LoginInfoMessage infobox = new LoginInfoMessage();
			infobox.setMessage("");
			infobox.setEnabled(false);

			return infobox;
		}
		else if (all.size() == 1) {
			return all.get(0);
		}

		log.error("More than one row with infobox");

		return all.get(0);
	}

	public LoginInfoMessage save(LoginInfoMessage entity) {
		return loginInfoMessageDao.save(entity);
	}
}
