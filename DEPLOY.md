# Production deployment runbook

End-to-end guide for shipping the platform live. Assumes you have:

- A domain you own (e.g. `voxai.in`)
- Cloudflare account managing that domain's DNS
- A credit card (Hetzner: ₹ via INR billing; Vercel: free tier)
- Twilio account already wired (we only repoint webhooks)
- Supabase project already running (we keep using it)

Two public hostnames recommended:
- `api.voxai.in` → backend stack on the VPS (Caddy fronts everything)
- `app.voxai.in` → React frontend on Vercel

Everything else (`ai-conversation-service`, `notification-service`, the
Postgres pooler) stays on the docker network or talks to managed services
directly — no public exposure.

---

## 0. One-time accounts

| Account | Why | Cost |
|---|---|---|
| Hetzner Cloud | the VPS | ~₹1500/mo (CCX13: 2 vCPU, 8 GB RAM, 80 GB SSD, EU/US region) |
| Cloudflare | DNS + DDoS edge | free |
| Vercel | static hosting for /frontend | free (Hobby plan) |
| GitHub | git remote the VPS pulls from | free |

If you'd rather not give Hetzner KYC: DigitalOcean Droplet 4 GB ($24/mo)
is identical from this guide's perspective — every command stays the same.

---

## 1. Provision the VPS

### 1a. Hetzner

1. Cloud Console → **+ Add Server**
2. Location: any (Nuremberg / Helsinki / Ashburn — Twilio voice latency is
   marginal across regions, pick whatever's closest to you).
3. Image: **Ubuntu 24.04**.
4. Type: **CCX13** (dedicated vCPU, 8 GB RAM) — ample headroom for all 8
   services. If you want to start cheaper, **CPX21** (3 vCPU shared, 4 GB,
   ~₹600/mo) works too, but you'll be in swap once a few calls pile up.
5. Networking: enable **IPv4**. IPv6 is fine but Twilio doesn't use it.
6. SSH keys: paste your `~/.ssh/id_ed25519.pub`. **Do not** use password auth.
7. Name: `voxai-prod-01`.
8. Create. Hetzner gives you an IPv4 like `5.78.123.45`.

### 1b. DigitalOcean (alternative)

1. Create → **Droplets** → Ubuntu 24.04 → Basic shared-CPU → **4 GB / 80 GB SSD**.
2. Choose a datacenter region (Bangalore for India users).
3. SSH key auth (paste pubkey). Hostname `voxai-prod-01`.
4. Create. Note the public IPv4.

---

## 2. Initial server hardening (10 minutes)

SSH in as root (Hetzner emails the IP; DO shows it on the droplet page):

```bash
ssh root@5.78.123.45
```

### Create a non-root user, lock down SSH

```bash
adduser deploy --disabled-password --gecos ""
usermod -aG sudo deploy
mkdir -p /home/deploy/.ssh
cp ~/.ssh/authorized_keys /home/deploy/.ssh/authorized_keys
chown -R deploy:deploy /home/deploy/.ssh
chmod 700 /home/deploy/.ssh
chmod 600 /home/deploy/.ssh/authorized_keys

# Disable root login + password auth in SSH.
sed -i 's/^#\?PermitRootLogin .*/PermitRootLogin no/' /etc/ssh/sshd_config
sed -i 's/^#\?PasswordAuthentication .*/PasswordAuthentication no/' /etc/ssh/sshd_config
systemctl restart ssh
```

Open a **new** terminal and verify: `ssh deploy@5.78.123.45` should work,
`ssh root@5.78.123.45` should now fail. Don't close the old terminal until
you've confirmed.

### Firewall (ufw) — only 22, 80, 443

```bash
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable
```

### Install Docker

```bash
curl -fsSL https://get.docker.com | sh
usermod -aG docker deploy
```

Log out and back in as `deploy` so the group membership takes effect.

### Optional: enable swap (cheap insurance)

```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

---

## 3. DNS — Cloudflare

Add two A records pointing at the VPS:

| Name | Type | Content | Proxy |
|---|---|---|---|
| `api` | A | `5.78.123.45` | **DNS only** (grey cloud) |
| `app` | CNAME | `cname.vercel-dns.com` | DNS only (Vercel handles it) |

**Important:** `api.voxai.in` must be **DNS only** (grey cloud), NOT
proxied (orange cloud). Caddy issues its own Let's Encrypt cert directly
to the VPS, and Cloudflare's proxy would interfere with the TLS-ALPN-01
challenge. Once Caddy is happy you can flip the proxy on if you want
DDoS protection — but most teams leave it grey.

DNS takes 1–5 minutes to propagate. Verify with:

```bash
dig +short api.voxai.in
# → 5.78.123.45
```

---

## 4. Clone repo + lay down secrets

As `deploy@vps`:

```bash
sudo apt install -y git make
cd /opt
sudo mkdir voxai && sudo chown deploy:deploy voxai
cd voxai
git clone https://github.com/<your-org>/ai-customer-service-platform.git .
```

### Secrets

Production secrets must NEVER live in git. They live at
`/etc/secrets/<service>/secrets.properties` on the VPS — the existing
`docker-compose.yml` already mounts them read-only into each container.

Create them:

```bash
sudo mkdir -p /etc/secrets/{auth-service,user-business-service,knowledge-service,incoming-call-service,call-orchestration-service,ai-conversation-service,conversation-summary-service,notification-service}
```

For each service, copy the dev `secrets/secrets.properties` from the repo
and **rotate every secret value**:

```bash
sudo cp /opt/voxai/auth-service/secrets/secrets.properties /etc/secrets/auth-service/secrets.properties
sudo nano /etc/secrets/auth-service/secrets.properties     # edit
sudo chmod 600 /etc/secrets/auth-service/secrets.properties
```

Repeat for all 8 services. **Generate new values for**:

| Key | How to generate |
|---|---|
| `secrets.jwt.secret` | `openssl rand -base64 64` (must be **identical across every service**) |
| `secrets.datasource.password` | your Supabase pooler password |
| `secrets.services.<svc>.password` | `openssl rand -hex 32` — same value in both auth-service's secrets AND the calling service's `secrets.authService.clientSecret` |
| `secrets.twilio.account-sid` / `auth-token` | from Twilio console |
| `secrets.elevenlabs.apiKey` / `secrets.deepgram.key` | from each vendor |
| `secrets.llm.gemini.apiKey` | from Google AI Studio |
| `secrets.whatsapp.accessToken` | from Meta (only needed when stubMode=false) |

Double-check ownership — files must be readable by Docker (root inside
container reads them):

```bash
sudo chown -R root:root /etc/secrets
sudo find /etc/secrets -type f -exec chmod 600 {} \;
```

---

## 5. Bring the stack up

```bash
cd /opt/voxai

# Required env vars for the production compose file.
cat <<EOF | sudo tee /opt/voxai/.env
PUBLIC_HOST=api.voxai.in
ACME_EMAIL=ops@voxai.in
CONFIGS_CALLORCHESTRATION_WSBASEURL=wss://api.voxai.in/call-orchestration-service
# Set when you've registered Meta templates; until then keep stub on.
CONFIGS_WHATSAPP_STUBMODE=true
CONFIGS_DASHBOARD_LEAD_LINK=https://app.voxai.in/leads/
EOF

docker compose -f docker-compose.yml up -d --build
```

First boot takes ~3 minutes (image build + Flyway migrations + Caddy fetches
TLS certs). Watch:

```bash
docker compose logs -f caddy-local     # (the prod profile reuses the same caddy image)
```

Wait for `certificate obtained successfully` from Caddy and `Started …
Application in X.X seconds` from each Spring app. Then:

```bash
curl -sI https://api.voxai.in/auth-service/api/v1/health
# → HTTP/2 200
```

If you get a TLS error: DNS hasn't propagated. If you get 502: container
isn't healthy yet — `docker compose ps` to see which.

---

## 6. Frontend on Vercel

### 6a. Connect the repo

1. vercel.com → **Add New → Project** → import the GitHub repo.
2. Framework preset: **Vite**.
3. Root directory: `frontend`.
4. Build command (default): `npm run build`.
5. Output directory (default): `dist`.
6. Environment variables: leave empty for now — config is in vercel.json.

### 6b. Add `frontend/vercel.json` (one-time commit)

Vite dev proxies `/api/<service>` → backend with path rewrite. The Vercel
deployment needs the same rewrites so the production frontend is
same-origin (no CORS pain). Create:

```bash
# In your laptop's repo, not on the VPS
cat > frontend/vercel.json <<'EOF'
{
  "rewrites": [
    { "source": "/api/auth/:path*",      "destination": "https://api.voxai.in/auth-service/:path*" },
    { "source": "/api/business/:path*",  "destination": "https://api.voxai.in/user-business-service/:path*" },
    { "source": "/api/knowledge/:path*", "destination": "https://api.voxai.in/knowledge-service/:path*" },
    { "source": "/api/calls/:path*",     "destination": "https://api.voxai.in/call-orchestration-service/:path*" },
    { "source": "/api/summary/:path*",   "destination": "https://api.voxai.in/conversation-summary-service/:path*" }
  ]
}
EOF
git add frontend/vercel.json
git commit -m "Vercel rewrites for API paths"
git push
```

Vercel auto-deploys on push.

### 6c. Custom domain

1. Vercel project → **Settings → Domains** → add `app.voxai.in`.
2. Vercel says "Add a CNAME pointing to `cname.vercel-dns.com`." You did
   this in §3 already.
3. Wait 1–2 min. Vercel issues TLS automatically.

Open `https://app.voxai.in` — login page should render. Sign in flow
should hit `https://app.voxai.in/api/auth/…` which Vercel rewrites to
`https://api.voxai.in/auth-service/…`. Verify in browser devtools that
requests succeed.

---

## 7. Repoint Twilio

In Twilio Console → Phone Numbers → your live number → **Voice & Fax
Configuration**:

- **A Call Comes In** → Webhook → `https://api.voxai.in/incoming-call-service/api/v1/webhook/twilio/voice` (POST)
- **Call Status Changes** → `https://api.voxai.in/incoming-call-service/api/v1/webhook/twilio/status` (POST)

Save. Place a test call. Confirm:

```bash
docker compose logs -f incoming-call-service call-orchestration-service
# you should see the inbound voice webhook + the media stream WS open
```

---

## 8. Deploy updates — the simple loop

On your laptop:

```bash
git push origin main
```

On the VPS (or via SSH from your laptop):

```bash
cd /opt/voxai && git pull && docker compose up -d --build
```

For convenience, add this to `/opt/voxai/Makefile` (commit it):

```makefile
.PHONY: deploy logs ps restart
deploy:
	git pull && docker compose up -d --build

logs:
	docker compose logs -f --tail=100 $(svc)

ps:
	docker compose ps

restart:
	docker compose restart $(svc)
```

Now: `cd /opt/voxai && make deploy`.

If you prefer single-command deploys from your laptop:

```bash
# laptop ~/.ssh/config
Host voxai-prod
  HostName 5.78.123.45
  User deploy

# laptop, in repo:
git push && ssh voxai-prod 'cd /opt/voxai && make deploy'
```

Vercel deploys the frontend automatically on the same push.

---

## 9. Observability — bare minimum

Logs:
```bash
docker compose logs -f --tail=100 call-orchestration-service
```

For a real platform: Loki + Grafana via docker compose is ~30 min to add
when you need it. Until then, `docker compose logs` is fine.

Disk:
```bash
df -h /var/lib/docker
docker system prune -af --volumes    # monthly housekeeping
```

Java heap: each service is configured with sensible defaults. If you see
OOM on the JVM, edit the service's `Dockerfile` and add
`ENV JAVA_OPTS="-Xmx512m -Xms256m"`.

---

## 10. Backups — what to actually back up

- **Postgres**: Supabase takes daily backups on free + paid tiers. Check the
  retention window and bump it if you need point-in-time recovery.
- **Secrets** (`/etc/secrets/*`): copy to your password manager (1Password
  / Bitwarden). They don't live anywhere else.
- **The VPS itself**: Hetzner / DO offer weekly snapshots for ~10% extra.
  Cheap insurance against a botched `apt upgrade`.

Code is on GitHub — that's the source of truth. The VPS is reproducible
from the README + this file.

---

## 11. Going to WhatsApp live mode

When Meta approves your templates (see `notification-service/META_WHATSAPP_SETUP.md`):

```bash
# Edit /opt/voxai/.env
CONFIGS_WHATSAPP_STUBMODE=false

# Edit notification-service's prod secret file
sudo nano /etc/secrets/notification-service/secrets.properties
# Set: secrets.whatsapp.accessToken=<meta token>
# Set: configs.whatsapp.phoneNumberId=<meta phone id> (or via env in compose)

cd /opt/voxai && docker compose up -d notification-service
```

Verify with a test call:
```bash
docker compose logs -f notification-service | grep '\[wa\]'
# should see `[wa] sent template=owner_new_lead to=…` instead of `[wa-stub]`
```

---

## 12. Things that will break on day one (and the fix)

| Symptom | Cause | Fix |
|---|---|---|
| Caddy logs "no such host" forever | DNS hasn't propagated | `dig api.voxai.in` — wait until it returns the VPS IP, then `docker compose restart caddy-local` |
| Caddy TLS challenge fails | Cloudflare proxy is ON (orange cloud) | Switch to DNS only (grey) for `api.*` |
| Login works, dashboard 403s everywhere | Old token from stub-mode session | Sign out + in to get a fresh JWT with current scopes |
| Twilio call connects but ai-conv doesn't reply | WS URL mismatch | `CONFIGS_CALLORCHESTRATION_WSBASEURL` must be `wss://api.voxai.in/...` not `ws://localhost/...` |
| Service won't start, "secrets file not found" | Secrets dir/permissions wrong | `sudo ls /etc/secrets/<svc>/secrets.properties` should exist and be `root:root` 600 |
| Hikari "FATAL: too many connections" | Supabase pooler maxed | Lower `configs.businessDb.pool.maximumPoolSize` per service or bump Supabase compute |

---

## 13. Future hardening (parked, not blocking)

These belong in `BACKLOG.md` but listed here as a quick reference:

- Add CORS bean OR keep going through Vercel rewrites (chosen path)
- Multi-VPS + load balancer (Hetzner LB ~₹500/mo) when single-box uptime matters
- Move from `make deploy` to GitHub Actions deploy-on-push (the runner SSHs in)
- Loki + Grafana for centralised logs
- Sentry SDK for error tracking (paid past free tier)
- Refresh-token rotation + revocation store (Redis) — see BACKLOG §Auth
- Caddy → Cloudflare proxied (orange cloud) once Caddy cert is steady, for DDoS
- Hetzner snapshot schedule

When any of these become "actually needed" rather than "nice to have",
revisit. Premature infra is expensive.
