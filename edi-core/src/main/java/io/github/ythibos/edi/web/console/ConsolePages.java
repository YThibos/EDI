package io.github.ythibos.edi.web.console;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class ConsolePages {

    @GetMapping("/login")
    String signInPage() {
        return "forward:/index.html";
    }
}
