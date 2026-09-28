package CleanVision.web;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import CleanVision.domain.DisposalFacility;
import CleanVision.domain.WasteCategory;
import CleanVision.dto.Forms.FacilityForm;
import CleanVision.repository.DisposalFacilityRepository;
import CleanVision.repository.ReportRepository;
import CleanVision.service.AppException;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/facilities")
@RequiredArgsConstructor
public class FacilityAdminController {

    private final DisposalFacilityRepository facilities;
    private final ReportRepository reports;

    @GetMapping
    public String list(Model model) {
        List<DisposalFacility> all = facilities.findAllByOrderByOperationalDescNameAsc();
        model.addAttribute("facilities", all);
        model.addAttribute("wasteTypes", Arrays.stream(WasteCategory.values())
                .filter(category -> category != WasteCategory.OTHER)
                .toList());
        model.addAttribute("facilityTypes", DisposalFacility.FacilityType.values());
        model.addAttribute("reportCount", all.stream()
                .collect(Collectors.toMap(DisposalFacility::getId,
                        facility -> reports.countByDisposalFacilityId(facility.getId()),
                        (left, right) -> left, java.util.LinkedHashMap::new)));
        return "admin/facilities";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("form")) {
            FacilityForm form = new FacilityForm();
            form.setFacilityType(DisposalFacility.FacilityType.MATERIAL_RECOVERY.name());
            form.setAcceptedTypes(List.of(WasteCategory.MIXED.name()));
            form.setOperational(true);
            model.addAttribute("form", form);
        }
        model.addAttribute("editing", false);
        model.addAttribute("facilityId", null);
        prepareOptions(model);
        return "admin/facility-form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") FacilityForm form, RedirectAttributes redirect) {
        try {
            DisposalFacility facility = new DisposalFacility();
            applyForm(facility, form);
            facilities.save(facility);
        } catch (AppException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/facilities/new";
        }
        redirect.addFlashAttribute("flashMessage", facilityName(form) + " added to the facility list");
        return "redirect:/admin/facilities";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        DisposalFacility facility = facilities.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Facility not found"));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", toForm(facility));
        }
        model.addAttribute("editing", true);
        model.addAttribute("facilityId", id);
        prepareOptions(model);
        return "admin/facility-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
            @ModelAttribute("form") FacilityForm form,
            RedirectAttributes redirect) {
        DisposalFacility facility = facilities.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Facility not found"));
        try {
            applyForm(facility, form);
            facilities.save(facility);
        } catch (AppException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/facilities/" + id + "/edit";
        }
        redirect.addFlashAttribute("flashMessage", facilityName(form) + " updated");
        return "redirect:/admin/facilities";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        DisposalFacility facility = facilities.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Facility not found"));
        long used = reports.countByDisposalFacilityId(id);
        if (used > 0) {
            redirect.addFlashAttribute("errorMessage",
                    facility.getName() + " is recorded on " + used
                            + " disposed report(s). Facility records are kept for auditing.");
            return "redirect:/admin/facilities";
        }
        facilities.delete(facility);
        redirect.addFlashAttribute("flashMessage", facility.getName() + " deleted");
        return "redirect:/admin/facilities";
    }

    private void prepareOptions(Model model) {
        model.addAttribute("wasteTypes", Arrays.stream(WasteCategory.values())
                .filter(category -> category != WasteCategory.OTHER)
                .toList());
        model.addAttribute("facilityTypes", DisposalFacility.FacilityType.values());
    }

    private void applyForm(DisposalFacility facility, FacilityForm form) {
        if (form.getName() == null || form.getName().isBlank()) {
            throw new AppException("Facility name is required");
        }
        facility.setName(form.getName().trim());

        DisposalFacility.FacilityType type;
        try {
            type = DisposalFacility.FacilityType.valueOf(form.getFacilityType());
        } catch (Exception ex) {
            throw new AppException("Choose a valid facility type");
        }
        facility.setFacilityType(type);
        facility.setLatitude(form.getLatitude());
        facility.setLongitude(form.getLongitude());
        facility.setAddress(blankToNull(form.getAddress()));
        facility.setWard(blankToNull(form.getWard()));
        facility.setCapacity(blankToNull(form.getCapacity()));
        facility.setContact(blankToNull(form.getContact()));
        facility.setOperational(form.isOperational());

        List<String> accepted = form.getAcceptedTypes() == null ? List.of() : form.getAcceptedTypes();
        if (accepted.isEmpty()) {
            throw new AppException("Choose at least one accepted waste type");
        }
        facility.setAcceptedWasteTypes(String.join(",", accepted));

        if (facility.getLatitude() == 0 && facility.getLongitude() == 0) {
            throw new AppException("Facility coordinates are required for routing");
        }
    }

    private FacilityForm toForm(DisposalFacility facility) {
        FacilityForm form = new FacilityForm();
        form.setName(facility.getName());
        form.setFacilityType(facility.getFacilityType().name());
        form.setLatitude(facility.getLatitude());
        form.setLongitude(facility.getLongitude());
        form.setAddress(facility.getAddress());
        form.setWard(facility.getWard());
        form.setAcceptedTypes(facility.getAcceptedCategories().stream()
                .map(Enum::name).toList());
        form.setCapacity(facility.getCapacity());
        form.setOperational(facility.isOperational());
        form.setContact(facility.getContact());
        return form;
    }

    private String facilityName(FacilityForm form) {
        return form.getName() == null || form.getName().isBlank() ? "Facility" : form.getName().trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
