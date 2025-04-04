package dk.digitalidentity.mvc.admin;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.NotAcceptableStatusException;

import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.security.RequireAdministrator;

@RequireAdministrator
@Controller
public class GroupController {

    @Autowired
    private GroupService groupService;

    @GetMapping("/admin/konfiguration/grupper")
    public String getGroupConfiguration(Model model) {
        List<Group> groups = groupService.getAll();

        model.addAttribute("groups", groups);

        return "admin/groups/list";
    }

    @GetMapping("/admin/konfiguration/grupper/{id}")
    public String viewGroup(Model model, @PathVariable("id") long id) {
        Group group = groupService.getById(id);
        if (id == 0 || group == null) {
            return "redirect:/admin/konfiguration/grupper";
        }

        model.addAttribute("group", group);
        model.addAttribute("groupDomainId", group.getDomain().getId());

        return "admin/groups/group";
    }

    @GetMapping("/admin/fragment/grupper/{id}/view")
    public String groupViewFragment(Model model, @PathVariable Long id) {
        Group group = groupService.getById(id);
        if (group == null) {
            throw new NotAcceptableStatusException("Ingen gruppe fundet");
        }

        model.addAttribute("group", group);

        return "admin/groups/fragments/group-details :: view";
    }

    @GetMapping("/admin/fragment/grupper/{id}/medlemmer/view")
    public String groupMembersViewFragment(Model model, @PathVariable Long id) {
        Group group = groupService.getById(id);
        if (group == null) {
            throw new NotAcceptableStatusException("Ingen gruppe fundet");
        }

        model.addAttribute("group", group);

        return "admin/groups/fragments/group-members-list :: view";
    }
}
