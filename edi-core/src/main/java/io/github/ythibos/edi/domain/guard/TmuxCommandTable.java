package io.github.ythibos.edi.domain.guard;

import java.util.List;
import java.util.Map;
import java.util.Optional;

final class TmuxCommandTable {

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("attach", "attach-session"), Map.entry("bind", "bind-key"), Map.entry("breakp", "break-pane"), Map.entry("capturep", "capture-pane"),
            Map.entry("clearhist", "clear-history"), Map.entry("clearphist", "clear-prompt-history"), Map.entry("confirm", "confirm-before"),
            Map.entry("deleteb", "delete-buffer"), Map.entry("detach", "detach-client"), Map.entry("menu", "display-menu"),
            Map.entry("display", "display-message"), Map.entry("displayp", "display-panes"), Map.entry("popup", "display-popup"),
            Map.entry("findw", "find-window"), Map.entry("has", "has-session"), Map.entry("if", "if-shell"), Map.entry("joinp", "join-pane"),
            Map.entry("killp", "kill-pane"), Map.entry("killw", "kill-window"), Map.entry("lastp", "last-pane"), Map.entry("last", "last-window"),
            Map.entry("linkw", "link-window"), Map.entry("lsb", "list-buffers"), Map.entry("lsc", "list-clients"), Map.entry("lscm", "list-commands"),
            Map.entry("lsk", "list-keys"), Map.entry("lsp", "list-panes"), Map.entry("ls", "list-sessions"), Map.entry("lsw", "list-windows"),
            Map.entry("loadb", "load-buffer"), Map.entry("lockc", "lock-client"), Map.entry("lock", "lock-server"), Map.entry("locks", "lock-session"),
            Map.entry("movep", "move-pane"), Map.entry("movew", "move-window"), Map.entry("new", "new-session"), Map.entry("neww", "new-window"),
            Map.entry("nextl", "next-layout"), Map.entry("next", "next-window"), Map.entry("pasteb", "paste-buffer"), Map.entry("pipep", "pipe-pane"),
            Map.entry("prevl", "previous-layout"), Map.entry("prev", "previous-window"), Map.entry("refresh", "refresh-client"),
            Map.entry("rename", "rename-session"), Map.entry("renamew", "rename-window"), Map.entry("resizep", "resize-pane"),
            Map.entry("resizew", "resize-window"), Map.entry("respawnp", "respawn-pane"), Map.entry("respawnw", "respawn-window"),
            Map.entry("rotatew", "rotate-window"), Map.entry("run", "run-shell"), Map.entry("saveb", "save-buffer"), Map.entry("selectl", "select-layout"),
            Map.entry("selectp", "select-pane"), Map.entry("selectw", "select-window"), Map.entry("send", "send-keys"), Map.entry("setb", "set-buffer"),
            Map.entry("setenv", "set-environment"), Map.entry("set", "set-option"), Map.entry("setw", "set-window-option"),
            Map.entry("showb", "show-buffer"), Map.entry("showenv", "show-environment"), Map.entry("showmsgs", "show-messages"),
            Map.entry("show", "show-options"), Map.entry("showphist", "show-prompt-history"), Map.entry("showw", "show-window-options"),
            Map.entry("source", "source-file"), Map.entry("splitw", "split-window"), Map.entry("start", "start-server"),
            Map.entry("suspendc", "suspend-client"), Map.entry("swapp", "swap-pane"), Map.entry("swapw", "swap-window"),
            Map.entry("switchc", "switch-client"), Map.entry("unbind", "unbind-key"), Map.entry("unlinkw", "unlink-window"), Map.entry("wait", "wait-for"));
    private static final List<String> NAMES = List.of("attach-session", "bind-key", "break-pane", "capture-pane", "choose-buffer", "choose-client",
            "choose-tree", "clear-history", "clear-prompt-history", "clock-mode", "command-prompt", "confirm-before", "copy-mode", "customize-mode",
            "delete-buffer", "detach-client", "display-menu", "display-message", "display-panes", "display-popup", "find-window", "has-session",
            "if-shell", "join-pane", "kill-pane", "kill-server", "kill-session", "kill-window", "last-pane", "last-window", "link-window",
            "list-buffers", "list-clients", "list-commands", "list-keys", "list-panes", "list-sessions", "list-windows", "load-buffer", "lock-client",
            "lock-server", "lock-session", "move-pane", "move-window", "new-session", "new-window", "next-layout", "next-window", "paste-buffer",
            "pipe-pane", "previous-layout", "previous-window", "refresh-client", "rename-session", "rename-window", "resize-pane", "resize-window",
            "respawn-pane", "respawn-window", "rotate-window", "run-shell", "save-buffer", "select-layout", "select-pane", "select-window",
            "send-keys", "send-prefix", "server-access", "set-buffer", "set-environment", "set-hook", "set-option", "set-window-option",
            "show-buffer", "show-environment", "show-hooks", "show-messages", "show-options", "show-prompt-history", "show-window-options",
            "source-file", "split-window", "start-server", "suspend-client", "swap-pane", "swap-window", "switch-client", "unbind-key",
            "unlink-window", "wait-for");

    private TmuxCommandTable() {
    }

    static Optional<String> commandNamed(String written) {
        if (ALIASES.containsKey(written)) {
            return Optional.of(ALIASES.get(written));
        }
        if (NAMES.contains(written)) {
            return Optional.of(written);
        }
        List<String> candidates = NAMES.stream().filter(name -> name.startsWith(written)).toList();
        return candidates.size() == 1 && !written.isEmpty() ? Optional.of(candidates.getFirst()) : Optional.empty();
    }
}
