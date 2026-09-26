package com.shiningcrescent.web;

import com.shiningcrescent.service.PortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/console/portal")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PortalController {
    private final PortalService portal;

    @GetMapping
    public Map<String, Object> get() {
        return portal.adminView();
    }

    @PutMapping("/brand")
    public Map<String, Object> brand(@RequestBody Map<String, Object> body, Principal p) {
        return portal.saveBrand(body, p.getName());
    }

    @PostMapping("/logo")
    public Map<String, Object> logo(@RequestParam("file") MultipartFile file, Principal p) {
        return portal.saveLogo(file, p.getName());
    }

    @PostMapping("/products/{id}/image")
    public Map<String, Object> productImage(@PathVariable Long id, @RequestParam("file") MultipartFile file, Principal p) {
        return portal.saveProductImage(id, file, p.getName());
    }

    @PutMapping("/smtp")
    public Map<String, Object> smtp(@RequestBody Map<String, Object> body, Principal p) {
        return portal.saveSmtp(body, p.getName());
    }

    @PostMapping("/smtp/test")
    public Map<String, Object> testMail(@RequestBody Map<String, String> body, Principal p) {
        return portal.sendTestMail(body.get("to"), p.getName());
    }
}
