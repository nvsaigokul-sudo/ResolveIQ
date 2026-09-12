"""
Resilient HTTP client for sending OTLP telemetry to ResolveIQ Ingestion Pipeline (PRD §15, §28).
"""

import time
import logging
from typing import Dict, Any, Optional
import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

from .models import OtlpMetricsBatch, OtlpLogsBatch, OtlpTracesBatch, DeploymentSpec

log = logging.getLogger(__name__)


class ResolveIqClient:
    """
    HTTP client communicating with ResolveIQ ingestion and incident endpoints.
    Employs exponential backoff and connection pooling for high-volume telemetry dispatch.
    """

    def __init__(
        self,
        ingestion_base_url: str = "http://localhost:8081",
        backend_base_url: str = "http://localhost:8080",
        api_key: Optional[str] = "riq_live_demo_api_key_for_simulation",
        jwt_token: Optional[str] = None,
        timeout_seconds: float = 10.0
    ):
        self.ingestion_base_url = ingestion_base_url.rstrip("/")
        self.backend_base_url = backend_base_url.rstrip("/")
        self.api_key = api_key
        self.jwt_token = jwt_token
        self.timeout = timeout_seconds

        # Configure connection pool and retries
        self.session = requests.Session()
        retries = Retry(
            total=3,
            backoff_factor=0.5,
            status_forcelist=[502, 503, 504],
            allowed_methods=["POST", "GET"]
        )
        adapter = HTTPAdapter(max_retries=retries, pool_connections=20, pool_maxsize=50)
        self.session.mount("http://", adapter)
        self.session.mount("https://", adapter)

    def _headers(self) -> Dict[str, str]:
        h = {"Content-Type": "application/json"}
        if self.jwt_token:
            h["Authorization"] = f"Bearer {self.jwt_token}"
        elif self.api_key:
            h["X-API-Key"] = self.api_key
        return h

    def send_metrics(self, batch: OtlpMetricsBatch) -> requests.Response:
        url = f"{self.ingestion_base_url}/v1/metrics"
        data = batch.model_dump(mode="json")
        res = self.session.post(url, json=data, headers=self._headers(), timeout=self.timeout)
        if res.status_code >= 400:
            log.warning("Metric ingestion error (%d): %s", res.status_code, res.text)
        return res

    def send_logs(self, batch: OtlpLogsBatch) -> requests.Response:
        url = f"{self.ingestion_base_url}/v1/logs"
        data = batch.model_dump(mode="json")
        res = self.session.post(url, json=data, headers=self._headers(), timeout=self.timeout)
        if res.status_code >= 400:
            log.warning("Log ingestion error (%d): %s", res.status_code, res.text)
        return res

    def send_traces(self, batch: OtlpTracesBatch) -> requests.Response:
        url = f"{self.ingestion_base_url}/v1/traces"
        data = batch.model_dump(mode="json")
        res = self.session.post(url, json=data, headers=self._headers(), timeout=self.timeout)
        if res.status_code >= 400:
            log.warning("Trace ingestion error (%d): %s", res.status_code, res.text)
        return res

    def get_incidents(self) -> Dict[str, Any]:
        url = f"{self.backend_base_url}/api/v1/incidents"
        res = self.session.get(url, headers=self._headers(), timeout=self.timeout)
        res.raise_for_status()
        return res.json()

    def get_incident_candidates(self, incident_id: str) -> Dict[str, Any]:
        url = f"{self.backend_base_url}/api/v1/incidents/{incident_id}/candidates"
        res = self.session.get(url, headers=self._headers(), timeout=self.timeout)
        res.raise_for_status()
        return res.json()

    def trigger_investigation(self, incident_id: str) -> Dict[str, Any]:
        url = f"{self.backend_base_url}/api/v1/incidents/{incident_id}/investigations"
        res = self.session.post(url, json={"forceRerun": True}, headers=self._headers(), timeout=self.timeout)
        res.raise_for_status()
        return res.json()

    def register_deployment(self, deployment: DeploymentSpec) -> requests.Response:
        url = f"{self.backend_base_url}/api/v1/deployments"
        data = {
            "serviceName": deployment.service,
            "version": deployment.version,
            "commitSha": deployment.commit_sha,
            "commitMessage": deployment.commit_message,
            "deployedBy": deployment.author,
            "status": deployment.status,
            "environment": "production"
        }
        res = self.session.post(url, json=data, headers=self._headers(), timeout=self.timeout)
        if res.status_code >= 400:
            log.warning("Deployment registration error (%d): %s", res.status_code, res.text)
        return res
