"""
Test suite for ResolveIQ Incident Simulator (PRD §28, §29, §43).
Validates YAML schemas, Pydantic data models, telemetry generation,
distributed trace DAG linking, and dry-run execution of all 10 scenarios.
"""

import unittest
from pathlib import Path
from simulator.models import ScenarioDefinition
from simulator.generator import TelemetryGenerator
from simulator.runner import ScenarioRunner


class TestScenarioDefinitionsAndTelemetry(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.scenarios_dir = Path("simulator/scenarios")
        cls.runner = ScenarioRunner(
            scenarios_dir=str(cls.scenarios_dir),
            tenant_id="00000000-0000-0000-0000-000000000001",
            dry_run=True
        )
        cls.generator = TelemetryGenerator(tenant_id="00000000-0000-0000-0000-000000000001")

    def test_all_ten_scenarios_exist_and_parse(self):
        """PRD §28.1: Verify all 10 required simulation scenarios exist and parse cleanly."""
        scenario_files = self.runner.list_available_scenarios()
        self.assertGreaterEqual(len(scenario_files), 10, "Must have at least 10 simulation scenarios")

        expected_ids = {
            "scenario-01-bad-deployment",
            "scenario-02-database-latency-spike",
            "scenario-03-memory-leak",
            "scenario-04-downstream-outage",
            "scenario-05-network-degradation",
            "scenario-06-legitimate-traffic-spike",
            "scenario-07-configuration-error",
            "scenario-08-cascading-failure",
            "scenario-09-authentication-outage",
            "scenario-10-third-party-api-failure",
        }

        loaded_ids = set()
        for f in scenario_files:
            sc = self.runner.load_scenario(str(f))
            self.assertIsInstance(sc, ScenarioDefinition)
            self.assertTrue(sc.id)
            self.assertTrue(sc.name)
            self.assertGreater(len(sc.topology), 0)
            self.assertIsNotNone(sc.baseline_phase)
            self.assertIsNotNone(sc.fault_phase)
            self.assertIsNotNone(sc.ground_truth)
            loaded_ids.add(sc.id)

        for eid in expected_ids:
            self.assertIn(eid, loaded_ids, f"Expected scenario {eid} was not discovered")

    def test_canonical_demo_scenario_spec(self):
        """PRD §29: Validate canonical demo scenario specification."""
        canonical = self.runner.load_scenario("01_bad_deployment.yaml")
        self.assertEqual(canonical.id, "scenario-01-bad-deployment")
        self.assertEqual(canonical.category, "DEPLOYMENT_REGRESSION")
        self.assertIsNotNone(canonical.deployment)
        self.assertEqual(canonical.deployment.service, "payment-service")
        self.assertEqual(canonical.deployment.version, "v2.8")
        self.assertEqual(canonical.deployment.commit_sha, "d7a4b81")

        # Topology verification
        services = [s.name for s in canonical.topology]
        self.assertIn("api-gateway", services)
        self.assertIn("user-service", services)
        self.assertIn("order-service", services)
        self.assertIn("payment-service", services)
        self.assertIn("inventory-service", services)
        self.assertIn("notification-service", services)

        # Ground truth
        self.assertTrue(canonical.ground_truth.expected_incident)
        self.assertEqual(canonical.ground_truth.expected_severity, "SEV1")
        self.assertEqual(canonical.ground_truth.root_service, "payment-service")
        self.assertGreaterEqual(canonical.ground_truth.min_confidence, 0.75)

    def test_scenario_06_false_positive_guard(self):
        """PRD §28.1, §60: Legitimate traffic spike must expect 0 incidents."""
        sc = self.runner.load_scenario("06_legitimate_traffic_spike.yaml")
        self.assertFalse(sc.ground_truth.expected_incident)
        self.assertEqual(sc.category, "BENIGN_TRAFFIC_SURGE")
        self.assertEqual(sc.fault_phase.error_rate_percent, 0.0)
        self.assertGreater(sc.fault_phase.request_rate, sc.baseline_phase.request_rate * 2.5)

    def test_telemetry_generator_metrics(self):
        """Verify metric generation adheres to OTLP schema."""
        canonical = self.runner.load_scenario("01_bad_deployment.yaml")
        svc = canonical.topology[0]
        batch = self.generator.generate_metrics(svc, canonical.fault_phase)

        self.assertEqual(batch.service_name, svc.name)
        self.assertGreater(len(batch.metrics), 0)
        for m in batch.metrics:
            self.assertTrue(m.metric_name)
            self.assertTrue(m.metric_type)
            self.assertEqual(m.service_name, svc.name)
            self.assertIn("tenant.id", m.resource_attributes)
            self.assertTrue(m.timestamp.endswith("Z"))

    def test_telemetry_generator_logs(self):
        """Verify log generation adheres to OTLP schema with fault patterns."""
        canonical = self.runner.load_scenario("01_bad_deployment.yaml")
        payment_svc = next(s for s in canonical.topology if s.name == "payment-service")
        batch = self.generator.generate_logs(payment_svc, canonical.fault_phase)

        self.assertEqual(batch.service_name, "payment-service")
        self.assertGreater(len(batch.logs), 1)
        error_logs = [l for l in batch.logs if l.severity == "ERROR"]
        self.assertGreater(len(error_logs), 0)

        # Check for HikariPool connection timeout error log
        has_hikari = any("HikariPool" in l.body for l in error_logs)
        self.assertTrue(has_hikari, "Must contain HikariPool connection error logs")

    def test_telemetry_generator_traces_dag_integrity(self):
        """Verify distributed trace spans correctly form a linked DAG across topology."""
        canonical = self.runner.load_scenario("01_bad_deployment.yaml")
        trace_batches = self.generator.generate_traces(canonical, canonical.fault_phase)

        self.assertGreater(len(trace_batches), 0)
        all_spans = []
        for tb in trace_batches:
            all_spans.extend(tb.spans)

        self.assertGreater(len(all_spans), 5)

        # Verify trace IDs are consistent across spans in a trace
        trace_ids = {s.trace_id for s in all_spans}
        self.assertGreater(len(trace_ids), 0)

        for tid in trace_ids:
            spans_for_trace = [s for s in all_spans if s.trace_id == tid]
            span_ids = {s.span_id for s in spans_for_trace}

            # Find root span (parent_span_id is None)
            root_spans = [s for s in spans_for_trace if s.parent_span_id is None]
            self.assertGreaterEqual(len(root_spans), 1, "Must have at least one root span")

            # Check that every child span has a valid parent_span_id in the same trace
            for s in spans_for_trace:
                if s.parent_span_id is not None:
                    self.assertIn(s.parent_span_id, span_ids, "Child span must reference a valid parent span ID")

    def test_dry_run_all_scenarios(self):
        """Verify dry-run execution of all scenarios passes completely."""
        results = self.runner.run_all()
        self.assertGreaterEqual(len(results), 10)
        for r in results:
            self.assertEqual(r.status, "PASSED", f"Scenario {r.scenario_id} failed: {r.error_message}")
            self.assertGreater(r.total_metrics, 0)
            self.assertGreater(r.total_logs, 0)
            self.assertGreater(r.total_spans, 0)


if __name__ == "__main__":
    unittest.main()
