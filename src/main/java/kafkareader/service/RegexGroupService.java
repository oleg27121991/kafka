package kafkareader.service;

import kafkareader.entity.RegexGroup;
import kafkareader.entity.RegexPattern;
import kafkareader.repository.RegexGroupRepository;
import kafkareader.repository.RegexPatternRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RegexGroupService {

    private final RegexGroupRepository regexGroupRepository;
    private final RegexPatternRepository regexPatternRepository;

    @Transactional(readOnly = true)
    public List<RegexGroup> getAllGroups() {
        return regexGroupRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<RegexGroup> getGroupById(Long id) {
        return regexGroupRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<RegexGroup> getGroupByName(String name) {
        return regexGroupRepository.findByName(name);
    }

    @Transactional
    public RegexGroup saveGroup(RegexGroup group) {
        return regexGroupRepository.save(group);
    }

    @Transactional
    public void deleteGroup(Long id) {
        regexGroupRepository.deleteById(id);
    }

    @Transactional
    public RegexPattern addPatternToGroup(Long groupId, RegexPattern pattern) {
        RegexGroup group = regexGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        group.addPattern(pattern);
        return regexPatternRepository.save(pattern);
    }

    @Transactional
    public void removePatternFromGroup(Long groupId, Long patternId) {
        RegexPattern pattern = regexPatternRepository.findById(patternId)
                .orElseThrow(() -> new RuntimeException("Pattern not found"));

        if (pattern.getRegexGroup() == null || !pattern.getRegexGroup().getId().equals(groupId)) {
            throw new RuntimeException("Pattern does not belong to the specified group");
        }

        pattern.getRegexGroup().removePattern(pattern);
        regexPatternRepository.delete(pattern);
    }
}