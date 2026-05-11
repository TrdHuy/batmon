# Agent Rules

## GitHub CLI Identity Isolation

To prevent identity conflicts between agents or local environments, all `gh` commands must use the project-local configuration directory:

```bash
GH_CONFIG_DIR=.config/gh gh <command>
```

Always ensure this environment variable is set before executing any GitHub CLI operations.
