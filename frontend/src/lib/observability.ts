export interface FrontendMetric {
  name: string;
  value: number;
  tags?: Record<string, string>;
  timestamp: string;
}

const metricsQueue: FrontendMetric[] = [];

export function recordLatency(endpoint: string, durationMs: number, status: number) {
  metricsQueue.push({
    name: "frontend.api.latency",
    value: durationMs,
    tags: {
      endpoint,
      status: String(status),
    },
    timestamp: new Date().toISOString(),
  });

  if (metricsQueue.length > 50) {
    metricsQueue.shift();
  }
}

export function reportClientError(error: Error, componentStack?: string) {
  console.error("[SRE Client Error]", {
    message: error.message,
    name: error.name,
    stack: error.stack,
    componentStack,
    timestamp: new Date().toISOString(),
  });
}
