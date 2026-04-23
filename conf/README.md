# Local Component Configuration

This directory records the single-node development components used by this backend.

## Files
- `local-components.yml`: structured inventory of local middleware, ports, roles, and important paths.
- `local-env.example`: optional environment-variable template for local services and tooling.
- `local-secrets.env`: private local secrets file ignored by Git.

## Rules
- Keep component endpoints, paths, and roles here.
- Do not commit real production secrets, host passwords, tokens, container IDs, PID files, or transient logs.
- For passwords, record the environment variable name or placeholder only.
- Put real local passwords in `local-secrets.env`; never remove it from `.gitignore`.
- If a component becomes a stable runtime dependency, update `local-components.yml` and refresh project memory with `tpp-refresh-project-memory`.
