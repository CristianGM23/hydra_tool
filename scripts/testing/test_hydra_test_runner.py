import argparse
import importlib.util
import json
from pathlib import Path
import unittest

MODULE_PATH = Path(__file__).with_name("hydra_test_runner.py")
SPEC = importlib.util.spec_from_file_location("hydra_test_runner", MODULE_PATH)
runner = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(runner)


class HydraTestRunnerTests(unittest.TestCase):
    def setUp(self):
        self.config = runner.load_config()

    def test_changed_frontend_selects_only_frontend(self):
        profile = self.config["profiles"]["fast"]
        tasks = runner.select_tasks(self.config, profile, ["frontend/src/App.tsx"])
        self.assertEqual(["frontend"], [task[0] for task in tasks])

    def test_shared_configuration_selects_both_suites(self):
        profile = self.config["profiles"]["fast"]
        tasks = runner.select_tasks(self.config, profile, ["config/hydra-test-profiles.json"])
        self.assertEqual({"backend", "frontend"}, {task[0] for task in tasks})

    def test_sharding_is_deterministic(self):
        tasks = [("backend", ["mvn"], Path(".")), ("frontend", ["npm"], Path("."))]
        self.assertEqual(["backend"], [task[0] for task in runner.shard(tasks, 0, 2)])
        self.assertEqual(["frontend"], [task[0] for task in runner.shard(tasks, 1, 2)])

    def test_backend_sharding_uses_an_include_file_instead_of_a_long_command_line(self):
        tasks = [("backend", ["./mvnw", "test"], runner.ROOT)]
        sharded = runner.shard_suites(tasks, 0, 2)
        self.assertTrue(any(argument.startswith("-Dsurefire.includesFile=") for argument in sharded[0][1]))

    def test_suite_sharding_passes_native_shard_arguments(self):
        tasks = [("frontend", ["npm", "run", "test:full"], Path("."))]
        sharded = runner.shard_suites(tasks, 1, 3)
        self.assertEqual(["npm", "run", "test:full", "--", "--shard=2/3"], sharded[0][1])

    def test_profiles_are_explicit_and_native_is_opt_in(self):
        profiles = self.config["profiles"]
        self.assertIn("parallel", profiles)
        self.assertIn("-Phydra-test-native", profiles["native"]["backend"])
        self.assertNotIn("-Phydra-test-native", profiles["fast"]["backend"])


if __name__ == "__main__":
    unittest.main()
