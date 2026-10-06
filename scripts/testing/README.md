# HYDRA test orchestration

`hydra_test_runner.py` reads `config/hydra-test-profiles.json` and supports:

- fast, full, native, parallel, frontend-only and backend-only profiles;
- changed-path selection;
- deterministic task sharding;
- content-addressed successful-run cache;
- bounded backend/frontend parallelism;
- separate logs;
- dry-run planning.

Examples:

```cmd
python scripts\testing\hydra_test_runner.py --profile fast
python scripts\testing\hydra_test_runner.py --profile full --no-cache
python scripts\testing\hydra_test_runner.py --profile native
python scripts\testing\hydra_test_runner.py --profile fast --changed frontend/src/App.tsx
python scripts\testing\hydra_test_runner.py --profile full --shard-count 2 --shard-index 0
```
