# Order Service — Kubernetes Production Setup

Complete production-grade K8s configuration for `order-service` demonstrating
every major Kubernetes feature with scripts to verify each one.

---

## File Structure

```
k8s/
├── order-deployment.yaml   # Deployment with all production settings
├── order-service.yaml      # ClusterIP + NodePort (same file, --- separator)
├── order-hpa.yaml          # HorizontalPodAutoscaler
├── order-pdb.yaml          # PodDisruptionBudget
├── order-configmap.yaml    # Non-sensitive configuration
├── order-secret.yaml       # Sensitive credentials (base64)
├── scripts/
│   ├── 00-deploy-all.bat          # Apply all manifests in correct order
│   ├── 01-test-rolling-update.bat # Prove zero-downtime deployment
│   ├── 01-traffic-loop.bat        # Background traffic (called by 01)
│   ├── 02-test-hpa.bat            # Trigger and observe HPA scale-out
│   ├── 03-test-self-healing.bat   # Kill pod, watch K8s restart it
│   ├── 04-test-pdb.bat            # Prove PDB blocks total eviction
│   ├── 05-test-resource-limits.bat# Show QoS, live CPU/mem usage
│   ├── 06-test-rollback.bat       # Bad deploy → instant rollback
│   ├── 07-watch-all.bat           # Live dashboard (run in 2nd terminal)
│   └── 08-cleanup.bat             # Tear down everything
└── load-tests/
    └── order-k8s-load-test.jmx   # JMeter plan: 5 test scenarios
```

---

## Prerequisites

```
Docker Desktop with Kubernetes enabled  (you already have this — v1.34.1)
kubectl on PATH
curl on PATH
JMeter 5.6+ (for load-tests)
```

Install metrics-server (required for HPA and kubectl top):
```bat
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

REM Docker Desktop needs this extra flag:
kubectl patch deployment metrics-server -n kube-system --type=json ^
  -p="[{\"op\":\"add\",\"path\":\"/spec/template/spec/containers/0/args/-\",\"value\":\"--kubelet-insecure-tls\"}]"
```

---

## Quick Start

```bat
cd k8s\scripts
00-deploy-all.bat
```

Order service will be reachable at **http://localhost:30081/api/orders**

---

## Kubernetes Features — What Was Built and Why

### 1. Rolling Update Strategy
**File:** `order-deployment.yaml` → `spec.strategy`

```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxUnavailable: 0   # never kill old pod before new one is Ready
    maxSurge: 1         # spin up 1 extra pod during update
```

`maxUnavailable: 0` is the key setting. K8s will not terminate an old pod
until the new one has passed its readiness probe. Combined with the preStop
sleep (10s) and graceful shutdown timeout (30s), this guarantees zero downtime.

**Verify:** `scripts/01-test-rolling-update.bat`
**Expected result:** Zero HTTP errors in the error log during the rollout.

---

### 2. Liveness, Readiness, and Startup Probes
**File:** `order-deployment.yaml` → `containers[0]`

| Probe | Endpoint | Purpose |
|---|---|---|
| startupProbe | `/actuator/health` | Gives Spring Boot up to 300s to start before liveness kicks in |
| livenessProbe | `/actuator/health/liveness` | Restarts pod if it becomes deadlocked or unresponsive |
| readinessProbe | `/actuator/health/readiness` | Removes pod from Service endpoints if it can't serve traffic |

The readiness probe is what prevents 503s during scale-up and rolling updates.
A pod only joins the Service load balancer after passing this probe.

**Verify:** `scripts/03-test-self-healing.bat` (liveness), `scripts/01-test-rolling-update.bat` (readiness)

---

### 3. Resource Requests and Limits
**File:** `order-deployment.yaml` → `containers[0].resources`

```yaml
resources:
  requests:
    cpu: 250m      # scheduler reserves this on a node
    memory: 512Mi
  limits:
    cpu: 500m      # pod is throttled if it exceeds this
    memory: 1Gi    # pod is OOMKilled if it exceeds this
```

Setting requests enables the scheduler to make placement decisions.
Setting limits prevents one runaway pod from starving other services.
The JVM is configured with `-XX:MaxRAMPercentage=75.0` so the heap
stays under the 1Gi memory limit.

**QoS Class:** `Burstable` (requests < limits). To get `Guaranteed`,
set requests == limits.

**Verify:** `scripts/05-test-resource-limits.bat`

---

### 4. Horizontal Pod Autoscaler (HPA)
**File:** `order-hpa.yaml`

```
minReplicas: 2   maxReplicas: 8
Scale up  when: avg CPU > 60% OR avg memory > 70%
Scale up  speed: up to 2 pods per 30s, stabilization 30s
Scale down speed: 1 pod per 60s, stabilization 120s (prevents flapping)
```

The slow scale-down stabilization window is intentional — prevents the
classic "traffic spike ends, pods removed, next spike hits with too few pods"
flapping problem.

**Verify:** `scripts/02-test-hpa.bat`
**Watch live:** `kubectl get hpa order-service-hpa --watch`

---

### 5. PodDisruptionBudget (PDB)
**File:** `order-pdb.yaml`

```yaml
minAvailable: 1
```

Guarantees at least 1 order-service pod is running during any voluntary
disruption — node drain, cluster upgrade, manual eviction. K8s will block
the disruption if honoring it would violate this budget.

Involuntary disruptions (node hardware failure) are NOT covered by PDB —
those require multi-node clusters.

**Verify:** `scripts/04-test-pdb.bat`

---

### 6. ConfigMap — Externalized Configuration
**File:** `order-configmap.yaml`

Stores all non-sensitive config: DB host/port, Kafka bootstrap servers,
Eureka host, Redis host, Actuator settings, JVM flags, graceful shutdown
timeout. Injected into pods via `envFrom.configMapRef`.

Changing config without changing code = `kubectl apply -f order-configmap.yaml`
then `kubectl rollout restart deployment/order-service`.

---

### 7. Secret — Sensitive Credentials
**File:** `order-secret.yaml`

Stores DB password and JWT secret as base64-encoded opaque secrets.
Injected alongside ConfigMap via `envFrom.secretRef`.

In production, replace this with:
- **Sealed Secrets** (encrypt-at-rest in Git)
- **AWS Secrets Manager / Vault** (dynamic secret injection)

---

### 8. Service — ClusterIP + NodePort
**File:** `order-service.yaml`

| Service | Type | Port | Use |
|---|---|---|---|
| `order-service` | ClusterIP | 8081 | Internal pod-to-pod (api-gateway → order-service) |
| `order-service-nodeport` | NodePort | 30081 | External access, JMeter, curl testing |

ClusterIP is the default for production inter-service communication.
NodePort is only for local testing — use an Ingress in real clusters.

---

### 9. Anti-Affinity
**File:** `order-deployment.yaml` → `spec.template.spec.affinity`

```yaml
podAntiAffinity:
  preferredDuringSchedulingIgnoredDuringExecution:
    topologyKey: kubernetes.io/hostname
```

Tells the scheduler to prefer placing replicas on different nodes.
`preferred` (soft) not `required` (hard) so scheduling still succeeds
on a single-node local cluster like Docker Desktop.

---

### 10. Graceful Shutdown
**File:** `order-deployment.yaml` + `order-configmap.yaml`

Three layers work together:

1. `preStop: sleep 10` — gives the load balancer 10s to stop routing new requests to the pod before SIGTERM is sent
2. `SERVER_SHUTDOWN: graceful` — Spring Boot waits for in-flight requests to complete before shutting down
3. `terminationGracePeriodSeconds: 60` — K8s waits up to 60s for the container to exit cleanly before force-killing

---

### 11. Pod Rollback
**File:** Deployment (keeps revision history)

```bat
kubectl rollout history deployment/order-service   # see all revisions
kubectl rollout undo deployment/order-service       # go back 1 revision
kubectl rollout undo deployment/order-service --to-revision=2  # specific revision
```

**Verify:** `scripts/06-test-rollback.bat` — deploys a broken image,
watches pods fail, then rolls back and confirms health.

---

## Test Scenarios Summary

| Script | K8s Feature Tested | Pass Criteria |
|---|---|---|
| `01-test-rolling-update.bat` | Rolling update, readiness probe, preStop | Zero HTTP errors during rollout |
| `02-test-hpa.bat` | HPA scale-out + scale-in | Replicas increase above 2, return to 2 |
| `03-test-self-healing.bat` | Liveness probe, ReplicaSet self-healing | New pod Running within 60s |
| `04-test-pdb.bat` | PodDisruptionBudget | Eviction blocked when 1 pod left |
| `05-test-resource-limits.bat` | Resource requests/limits, QoS | Shows Burstable class, live usage |
| `06-test-rollback.bat` | Rollout undo | Service healthy after rollback |
| `order-k8s-load-test.jmx` | All of the above via JMeter | 0% error rate, p99 < 1s |

---

## Useful kubectl Commands

```bat
# Watch everything live
kubectl get pods,svc,hpa,pdb -l app=order-service -w

# Stream pod logs
kubectl logs -f -l app=order-service --all-containers

# Describe pod (see probe failures, events, resource usage)
kubectl describe pod <pod-name>

# Manual scale
kubectl scale deployment order-service --replicas=4

# Trigger rolling update (forces pod replacement without image change)
kubectl rollout restart deployment/order-service

# Check rollout status
kubectl rollout status deployment/order-service

# Rollback
kubectl rollout undo deployment/order-service

# See what changed between revisions
kubectl rollout history deployment/order-service --revision=2

# Port-forward directly to a specific pod (bypass Service)
kubectl port-forward pod/<pod-name> 9090:8081

# Exec into a pod for debugging
kubectl exec -it <pod-name> -- sh

# View HPA decisions in real time
kubectl get hpa order-service-hpa --watch

# Check if PDB is blocking anything
kubectl get pdb order-service-pdb -o yaml
```
