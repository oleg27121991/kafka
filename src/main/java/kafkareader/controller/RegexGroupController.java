package kafkareader.controller;

import kafkareader.entity.RegexGroup;
import kafkareader.entity.RegexPattern;
import kafkareader.service.RegexGroupService;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/regex-groups")
@RequiredArgsConstructor
public class RegexGroupController {

    private final RegexGroupService regexGroupService;

    @GetMapping
    public List<RegexGroup> getAllGroups() {
        return regexGroupService.getAllGroups();
    }

    @PostMapping
    public ResponseEntity<?> createGroup(@RequestBody RegexGroup group) {
        System.out.println("Received group: " + group);
        if (group.getName() == null) {
            return ResponseEntity.badRequest().body("Name is required");
        }
        RegexGroup saved = regexGroupService.saveGroup(group);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        regexGroupService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{groupId}/patterns")
    public ResponseEntity<List<RegexPattern>> getPatternsByGroup(@PathVariable Long groupId) {
        return regexGroupService.getGroupById(groupId)
                .map(group -> {
                    Hibernate.initialize(group.getPatterns());
                    return ResponseEntity.ok(group.getPatterns());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{groupId}/patterns")
    public ResponseEntity<RegexPattern> addPatternToGroup(@PathVariable Long groupId, @RequestBody RegexPattern pattern) {
        try {
            RegexPattern newPattern = regexGroupService.addPatternToGroup(groupId, pattern);
            return ResponseEntity.ok(newPattern);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{groupId}/patterns/{patternId}")
    public ResponseEntity<Void> removePatternFromGroup(@PathVariable Long groupId, @PathVariable Long patternId) {
        try {
            regexGroupService.removePatternFromGroup(groupId, patternId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}