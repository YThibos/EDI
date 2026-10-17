package io.github.ythibos.edi.web.memory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.memory.MemoryCheck;
import io.github.ythibos.edi.application.memory.MemoryQuery;
import io.github.ythibos.edi.domain.memory.StowList;

@RestController
class MemoryController {

    private final MemoryQuery memory;

    MemoryController(MemoryQuery memory) {
        this.memory = memory;
    }

    @GetMapping("/api/memory/check")
    ResponseEntity<MemoryCheck> check() {
        MemoryCheck check = memory.check();
        return ResponseEntity.status(check.passes() ? HttpStatus.OK : HttpStatus.CONFLICT).body(check);
    }

    @GetMapping("/api/memory/stow-list")
    StowList stowList() {
        return memory.stowList();
    }
}
